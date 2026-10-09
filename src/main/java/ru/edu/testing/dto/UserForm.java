package ru.edu.testing.dto;

import jakarta.validation.constraints.NotNull;
import ru.edu.testing.domain.Role;

/** Форма администратора: роль и блокировка пользователя. */
public class UserForm extends RegistrationForm {

    @NotNull(message = "Выберите роль")
    private Role role = Role.STUDENT;

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
