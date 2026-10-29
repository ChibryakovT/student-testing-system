package ru.edu.testing.domain;

import jakarta.persistence.*;

/** Пара в недельном расписании группы. */
@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private StudyGroup group;

    /** День недели: 1 — понедельник … 6 — суббота. */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    /** Номер пары (I–IV). */
    @Column(name = "pair_no", nullable = false)
    private int pairNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "week_type", nullable = false, length = 20)
    private WeekType weekType = WeekType.EVERY;

    /** Дисциплина; для занятий вне плана (кураторский час) — только название. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(length = 150)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    /** Второй преподаватель (практика по подгруппам). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "co_teacher_id")
    private User coTeacher;

    @Column(length = 30)
    private String room;

    public String getName() {
        return subject != null ? subject.getName() : title;
    }

    public String getTeachers() {
        String t = teacher == null ? "" : teacher.getFullName();
        if (coTeacher != null) {
            t = t.isEmpty() ? coTeacher.getFullName() : t + ", " + coTeacher.getFullName();
        }
        return t;
    }

    public Long getId() { return id; }
    public StudyGroup getGroup() { return group; }
    public void setGroup(StudyGroup group) { this.group = group; }
    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public int getPairNo() { return pairNo; }
    public void setPairNo(int pairNo) { this.pairNo = pairNo; }
    public WeekType getWeekType() { return weekType; }
    public void setWeekType(WeekType weekType) { this.weekType = weekType; }
    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }
    public User getCoTeacher() { return coTeacher; }
    public void setCoTeacher(User coTeacher) { this.coTeacher = coTeacher; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
}
