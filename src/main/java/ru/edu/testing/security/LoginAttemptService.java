package ru.edu.testing.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Защита от подбора пароля: после N неудачных попыток вход по логину
 * блокируется на заданное время.
 */
@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration lockDuration;
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${app.security.max-login-attempts:5}") int maxAttempts,
                               @Value("${app.security.lock-minutes:5}") long lockMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String key = key(String.valueOf(event.getAuthentication().getPrincipal()));
        if (attempts.size() > MAX_TRACKED) {
            // Защита от переполнения памяти перебором случайных логинов
            attempts.values().removeIf(a -> a.isExpired(lockDuration));
        }
        attempts.compute(key, (k, a) -> {
            Attempts current = (a == null || a.isExpired(lockDuration)) ? new Attempts() : a;
            current.count++;
            current.last = Instant.now();
            return current;
        });
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        attempts.remove(key(event.getAuthentication().getName()));
    }

    public boolean isBlocked(String username) {
        Attempts a = attempts.get(key(username));
        if (a == null) {
            return false;
        }
        if (a.isExpired(lockDuration)) {
            attempts.remove(key(username));
            return false;
        }
        return a.count >= maxAttempts;
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Attempts {
        private int count;
        private Instant last = Instant.now();

        boolean isExpired(Duration lock) {
            return last.plus(lock).isBefore(Instant.now());
        }
    }
}
