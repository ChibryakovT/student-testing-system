package ru.edu.testing.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.edu.testing.dto.ApiDtos.UserDto;
import ru.edu.testing.dto.RegistrationForm;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.UserService;

@RestController
@RequestMapping("/api")
public class AuthApiController {

    private final UserService userService;

    public AuthApiController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegistrationForm form) {
        return UserDto.of(userService.register(form));
    }

    /** Проверка учётных данных: возвращает профиль текущего пользователя. */
    @GetMapping("/auth/me")
    public UserDto me(@AuthenticationPrincipal AppUserDetails me) {
        return UserDto.of(userService.get(me.getId()));
    }
}
