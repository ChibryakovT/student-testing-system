package ru.edu.testing.dto;

import jakarta.validation.constraints.Size;

public class OptionForm {

    @Size(max = 500, message = "Вариант ответа не длиннее 500 символов")
    private String text;

    private boolean correct;

    public OptionForm() {
    }

    public OptionForm(String text, boolean correct) {
        this.text = text;
        this.correct = correct;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
}
