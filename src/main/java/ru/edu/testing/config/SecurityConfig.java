package ru.edu.testing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import ru.edu.testing.dto.ApiDtos.ErrorResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String CSP = "default-src 'self'; "
            + "script-src 'self' https://cdn.jsdelivr.net; "
            + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
            + "font-src 'self' https://cdn.jsdelivr.net; "
            + "img-src 'self' data:; "
            + "form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** REST API для Postman: HTTP Basic, без сессий и без CSRF (токен не нужен, cookie не используются). */
    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/reports/**").hasAnyRole("TEACHER", "ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint((req, res, ex) ->
                        writeJson(mapper, req, res, HttpStatus.UNAUTHORIZED, "Требуется аутентификация")))
                .exceptionHandling(ex -> ex.accessDeniedHandler((req, res, e) ->
                        writeJson(mapper, req, res, HttpStatus.FORBIDDEN, "Недостаточно прав")))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /** Веб-интерфейс: форма входа, сессия, CSRF-защита, заголовки безопасности. */
    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        .requestMatchers("/subjects/**", "/reports/**").hasAnyRole("TEACHER", "ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID"))
                .sessionManagement(s -> s.sessionFixation(f -> f.migrateSession()).maximumSessions(3))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .frameOptions(f -> f.deny()));
        return http.build();
    }

    private AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String reason = "error";
            if (exception instanceof LockedException || exception.getCause() instanceof LockedException) {
                reason = "locked";
            } else if (exception instanceof DisabledException) {
                reason = "disabled";
            }
            response.sendRedirect(request.getContextPath() + "/login?" + reason);
        };
    }

    private static void writeJson(ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res,
                                  HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), new ErrorResponse(LocalDateTime.now(), status.value(),
                status.getReasonPhrase(), message, req.getRequestURI(), List.of()));
    }
}
