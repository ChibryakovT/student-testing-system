package ru.edu.testing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Смена пароля в профиле. */
public class PasswordForm {

    @NotBlank(message = "Введите текущий пароль")
    private String currentPassword;

    @NotBlank(message = "Введите новый пароль")
    @Size(min = 8, max = 72, message = "Пароль: от 8 до 72 символов")
    @Pattern(regexp = "^(?=.*[A-Za-zА-Яа-я])(?=.*\\d).+$", message = "Пароль должен содержать буквы и цифры")
    private String newPassword;

    @NotBlank(message = "Повторите новый пароль")
    private String confirmPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
