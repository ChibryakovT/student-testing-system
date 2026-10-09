package ru.edu.testing.domain;

public enum QuestionType {
    SINGLE("Один вариант"),
    MULTIPLE("Несколько вариантов");

    private final String title;

    QuestionType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
