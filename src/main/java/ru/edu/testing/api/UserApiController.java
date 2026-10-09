package ru.edu.testing.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.edu.testing.domain.Role;
import ru.edu.testing.dto.ApiDtos.UserDto;
import ru.edu.testing.dto.UserForm;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.UserService;

import java.util.List;

/** Управление пользователями. Доступ: только администратор (см. SecurityConfig). */
@RestController
@RequestMapping("/api/users")
public class UserApiController {

    private final UserService userService;

    public UserApiController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list(@RequestParam(required = false) String q,
                              @RequestParam(required = false) Role role,
                              @RequestParam(defaultValue = "name") String sort) {
        return userService.search(q, role, sort).stream().map(UserDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody UserForm form) {
        return UserDto.of(userService.create(form, form.getRole()));
    }

    @PatchMapping("/{id}")
    public UserDto update(@PathVariable Long id, @RequestParam Role role, @RequestParam boolean enabled,
                          @AuthenticationPrincipal AppUserDetails me) {
        return UserDto.of(userService.update(id, role, enabled, me.getId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        userService.delete(id, me.getId());
    }
}
