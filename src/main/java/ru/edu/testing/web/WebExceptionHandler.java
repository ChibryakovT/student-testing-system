package ru.edu.testing.web;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.security.AppUserDetails;

/**
 * Обработка ошибок веб-интерфейса: пользователь видит понятное сообщение,
 * а технические подробности (stack trace, SQL) остаются только в логе сервера.
 */
@ControllerAdvice(basePackageClasses = WebExceptionHandler.class)
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ModelAttribute("me")
    public AppUserDetails currentUser(@AuthenticationPrincipal AppUserDetails me) {
        return me;
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public String notFound(Exception e, Model model, HttpServletResponse response) {
        return page(model, response, HttpStatus.NOT_FOUND,
                e instanceof NotFoundException ? e.getMessage() : "Страница не найдена");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String forbidden(AccessDeniedException e, Model model, HttpServletResponse response) {
        return page(model, response, HttpStatus.FORBIDDEN, "Недостаточно прав для этого действия");
    }

    @ExceptionHandler(BusinessException.class)
    public String business(BusinessException e, Model model, HttpServletResponse response) {
        return page(model, response, e.getStatus(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public String badRequest(Exception e, Model model, HttpServletResponse response) {
        return page(model, response, HttpStatus.BAD_REQUEST, "Некорректные параметры запроса");
    }

    @ExceptionHandler(Exception.class)
    public String unexpected(Exception e, Model model, HttpServletResponse response) {
        log.error("Необработанная ошибка", e);
        return page(model, response, HttpStatus.INTERNAL_SERVER_ERROR,
                "Внутренняя ошибка сервера. Попробуйте повторить действие позже");
    }

    private static String page(Model model, HttpServletResponse response, HttpStatus status, String message) {
        response.setStatus(status.value());
        model.addAttribute("status", status.value());
        model.addAttribute("message", message);
        return "error/page";
    }
}
