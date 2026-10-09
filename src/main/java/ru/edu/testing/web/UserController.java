package ru.edu.testing.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.edu.testing.domain.Role;
import ru.edu.testing.dto.UserForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.UserService;

/** Управление пользователями. Доступно только администратору (см. SecurityConfig). */
@Controller
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Role role,
                       @RequestParam(defaultValue = "name") String sort, Model model) {
        model.addAttribute("users", userService.search(q, role, sort));
        model.addAttribute("roles", Role.values());
        model.addAttribute("q", q);
        model.addAttribute("role", role);
        model.addAttribute("sort", sort);
        return "users/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new UserForm());
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") UserForm form, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        model.addAttribute("roles", Role.values());
        if (result.hasErrors()) {
            return "users/form";
        }
        try {
            userService.create(form, form.getRole());
        } catch (BusinessException e) {
            result.reject("user", e.getMessage());
            return "users/form";
        }
        redirect.addFlashAttribute("success", "Пользователь создан");
        return "redirect:/users";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam Role role,
                         @RequestParam(defaultValue = "false") boolean enabled,
                         @AuthenticationPrincipal AppUserDetails me, RedirectAttributes redirect) {
        try {
            userService.update(id, role, enabled, me.getId());
            redirect.addFlashAttribute("success", "Данные пользователя обновлены");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                         RedirectAttributes redirect) {
        try {
            userService.delete(id, me.getId());
            redirect.addFlashAttribute("success", "Пользователь удалён");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }
}
