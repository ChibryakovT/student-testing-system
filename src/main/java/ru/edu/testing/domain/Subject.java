package ru.edu.testing.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_form", nullable = false, length = 20)
    private ControlForm controlForm = ControlForm.EXAM;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ControlForm getControlForm() { return controlForm; }
    public void setControlForm(ControlForm controlForm) { this.controlForm = controlForm; }
}
