package ru.edu.testing.dto;

/** Колонки ведомости, которые можно включать и отключать. */
public enum SheetColumn {
    NUM("№"),
    NAME("ФИО"),
    GROUP("Группа"),
    BOOK("№ зачётки"),
    SCORE("Баллы"),
    PERCENT("%"),
    GRADE("Оценка"),
    GRADE_WORD("Прописью"),
    DATE("Дата сдачи"),
    SIGN("Подпись");

    private final String title;

    SheetColumn(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
