package ru.edu.testing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TestForm {

    @NotBlank(message = "Укажите название теста")
    @Size(max = 200, message = "Название не длиннее 200 символов")
    private String title;

    @Size(max = 1000, message = "Описание не длиннее 1000 символов")
    private String description;

    @NotNull(message = "Выберите дисциплину")
    private Long subjectId;

    @NotNull(message = "Укажите лимит времени")
    @Min(value = 1, message = "Лимит времени — от 1 минуты")
    @Max(value = 300, message = "Лимит времени — не более 300 минут")
    private Integer timeLimitMinutes = 20;

    @NotNull(message = "Укажите проходной балл")
    @Min(value = 0, message = "Проходной балл — от 0 до 100%")
    @Max(value = 100, message = "Проходной балл — от 0 до 100%")
    private Integer passingScore = 60;

    private boolean published;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public Integer getTimeLimitMinutes() { return timeLimitMinutes; }
    public void setTimeLimitMinutes(Integer timeLimitMinutes) { this.timeLimitMinutes = timeLimitMinutes; }
    public Integer getPassingScore() { return passingScore; }
    public void setPassingScore(Integer passingScore) { this.passingScore = passingScore; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
}
