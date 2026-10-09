package ru.edu.testing.domain;

public enum Role {
    ADMIN("Администратор"),
    TEACHER("Преподаватель"),
    STUDENT("Студент");

    private final String title;

    Role(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
