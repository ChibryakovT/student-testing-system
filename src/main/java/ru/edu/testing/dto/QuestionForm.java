package ru.edu.testing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.edu.testing.domain.QuestionType;

import java.util.ArrayList;
import java.util.List;

public class QuestionForm {

    public static final int MAX_OPTIONS = 6;

    @NotBlank(message = "Введите текст вопроса")
    @Size(max = 1000, message = "Текст вопроса не длиннее 1000 символов")
    private String text;

    @NotNull(message = "Выберите тип вопроса")
    private QuestionType type = QuestionType.SINGLE;

    @NotNull(message = "Укажите количество баллов")
    @Min(value = 1, message = "Баллы — от 1 до 100")
    @Max(value = 100, message = "Баллы — от 1 до 100")
    private Integer points = 1;

    @Valid
    @Size(max = MAX_OPTIONS, message = "Не более 6 вариантов ответа")
    private List<OptionForm> options = new ArrayList<>();

    /** Дополняет список пустыми строками, чтобы в форме всегда было 6 полей. */
    public QuestionForm padOptions() {
        while (options.size() < MAX_OPTIONS) {
            options.add(new OptionForm());
        }
        return this;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public QuestionType getType() { return type; }
    public void setType(QuestionType type) { this.type = type; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public List<OptionForm> getOptions() { return options; }
    public void setOptions(List<OptionForm> options) { this.options = options; }
}
