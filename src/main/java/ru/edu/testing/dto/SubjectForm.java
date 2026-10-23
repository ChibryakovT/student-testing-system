package ru.edu.testing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.edu.testing.domain.ControlForm;
import jakarta.validation.constraints.Size;

public class SubjectForm {

    @NotBlank(message = "Укажите название дисциплины")
    @Size(max = 100, message = "Название не длиннее 100 символов")
    private String name;

    @Size(max = 500, message = "Описание не длиннее 500 символов")
    private String description;

    @NotNull(message = "Выберите форму контроля")
    private ControlForm controlForm = ControlForm.EXAM;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ControlForm getControlForm() { return controlForm; }
    public void setControlForm(ControlForm controlForm) { this.controlForm = controlForm; }
}
