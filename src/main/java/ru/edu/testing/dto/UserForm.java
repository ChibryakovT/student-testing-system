package ru.edu.testing.dto;

import jakarta.validation.constraints.NotNull;
import ru.edu.testing.domain.Role;

/** Форма администратора: роль и блокировка пользователя. */
public class UserForm extends RegistrationForm {

    @NotNull(message = "Выберите роль")
    private Role role = Role.STUDENT;

    /** Кафедра (для преподавателя). */
    private Long departmentId;

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
