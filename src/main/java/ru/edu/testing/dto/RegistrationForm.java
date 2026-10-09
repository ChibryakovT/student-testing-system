package ru.edu.testing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank(message = "Укажите логин")
    @Size(min = 3, max = 50, message = "Логин: от 3 до 50 символов")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Логин может содержать только латиницу, цифры и символы _ . -")
    private String username;

    @NotBlank(message = "Укажите пароль")
    @Size(min = 8, max = 72, message = "Пароль: от 8 до 72 символов")
    @Pattern(regexp = "^(?=.*[A-Za-zА-Яа-я])(?=.*\\d).+$", message = "Пароль должен содержать буквы и цифры")
    private String password;

    @NotBlank(message = "Укажите ФИО")
    @Size(max = 150, message = "ФИО не длиннее 150 символов")
    private String fullName;

    @NotBlank(message = "Укажите e-mail")
    @Email(message = "Некорректный e-mail")
    @Size(max = 150)
    private String email;

    @Size(max = 30, message = "Название группы не длиннее 30 символов")
    private String groupName;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
}
