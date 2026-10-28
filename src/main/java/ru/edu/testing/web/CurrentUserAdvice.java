package ru.edu.testing.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import ru.edu.testing.security.AppUserDetails;

/**
 * Текущий пользователь доступен во всех шаблонах как ${me},
 * в том числе на стандартной странице ошибок (403, 404, 500).
 */
@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("me")
    public AppUserDetails currentUser(@AuthenticationPrincipal AppUserDetails me) {
        return me;
    }

    /** Текущий раздел сайта — для подсветки пункта меню. */
    @ModelAttribute("section")
    public String section(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String[] parts = path.split("/");
        return parts.length > 1 ? parts[1] : "";
    }
}
