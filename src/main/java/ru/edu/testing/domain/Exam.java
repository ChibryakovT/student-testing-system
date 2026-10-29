package ru.edu.testing.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

/** Экзамен или зачёт в сессию. Группа, дисциплина, преподаватель и форма контроля — из учебного плана. */
@Entity
@Table(name = "exams")
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id")
    private CurriculumItem plan;

    @Column(name = "exam_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(length = 30)
    private String room;

    public Long getId() { return id; }
    public CurriculumItem getPlan() { return plan; }
    public void setPlan(CurriculumItem plan) { this.plan = plan; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
}
