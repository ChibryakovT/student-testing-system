package ru.edu.testing.web;

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
}
