package ru.edu.testing.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.edu.testing.dto.PasswordForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.UserService;

/** Профиль пользователя: данные учётной записи и смена пароля. */
@Controller
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("user", userService.get(me.getId()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PasswordForm());
        }
        return "profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@Valid @ModelAttribute("form") PasswordForm form, BindingResult result,
                                 @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                userService.changePassword(me.getId(), form.getCurrentPassword(), form.getNewPassword(),
                        form.getConfirmPassword());
                redirect.addFlashAttribute("success", "Пароль изменён");
                return "redirect:/profile";
            } catch (BusinessException e) {
                result.reject("password", e.getMessage());
            }
        }
        model.addAttribute("user", userService.get(me.getId()));
        return "profile";
    }
}
