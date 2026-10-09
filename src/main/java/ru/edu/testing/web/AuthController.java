package ru.edu.testing.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.edu.testing.dto.RegistrationForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.UserService;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal AppUserDetails me) {
        return me != null ? "redirect:/" : "login";
    }

    @GetMapping("/register")
    public String registerForm(@ModelAttribute("form") RegistrationForm form) {
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult result,
                           RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "register";
        }
        try {
            userService.register(form);
        } catch (BusinessException e) {
            result.reject("register", e.getMessage());
            return "register";
        }
        redirect.addFlashAttribute("success", "Регистрация завершена. Войдите в систему");
        return "redirect:/login";
    }
}
