package ru.edu.testing.domain;

import jakarta.persistence.*;

/**
 * Строка учебного плана: дисциплина группы, преподаватель, семестр и форма контроля.
 * Именно отсюда система «знает», у какой группы какой предмет и кто его ведёт.
 */
@Entity
@Table(name = "curriculum")
public class CurriculumItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private StudyGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    private Integer semester;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_form", nullable = false, length = 20)
    private ControlForm controlForm = ControlForm.EXAM;

    public CurriculumItem() {
    }

    public CurriculumItem(StudyGroup group, Subject subject, User teacher, Integer semester, ControlForm controlForm) {
        this.group = group;
        this.subject = subject;
        this.teacher = teacher;
        this.semester = semester;
        this.controlForm = controlForm;
    }

    public Long getId() { return id; }
    public StudyGroup getGroup() { return group; }
    public void setGroup(StudyGroup group) { this.group = group; }
    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }
    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }
    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }
    public ControlForm getControlForm() { return controlForm; }
    public void setControlForm(ControlForm controlForm) { this.controlForm = controlForm; }
}
