package ru.edu.testing.domain;

/** Когда студент видит правильные ответы и пояснения к вопросам. */
public enum ShowAnswers {
    NEVER("Никогда"),
    AFTER_SUBMIT("Сразу после отправки"),
    AFTER_LAST_ATTEMPT("После последней попытки");

    private final String title;

    ShowAnswers(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
