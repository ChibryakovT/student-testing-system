package ru.edu.testing.domain;

/** Форма промежуточного контроля по дисциплине. */
public enum ControlForm {
    EXAM("экзамен"),
    CREDIT("зачёт"),
    GRADED_CREDIT("дифференцированный зачёт");

    private final String title;

    ControlForm(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** Зачёт без оценки: в ведомости «зачтено / не зачтено». */
    public boolean isPassFail() {
        return this == CREDIT;
    }
}
