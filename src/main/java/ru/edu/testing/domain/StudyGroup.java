package ru.edu.testing.domain;

import jakarta.persistence.*;

/** Учебная группа, например «2 ПИА»: курс, специальность, факультет. */
@Entity
@Table(name = "study_groups")
public class StudyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    private Integer course;

    @Column(length = 150)
    private String specialty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    public StudyGroup() {
    }

    public StudyGroup(String name, Integer course, String specialty, Faculty faculty) {
        this.name = name;
        this.course = course;
        this.specialty = specialty;
        this.faculty = faculty;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getCourse() { return course; }
    public void setCourse(Integer course) { this.course = course; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public Faculty getFaculty() { return faculty; }
    public void setFaculty(Faculty faculty) { this.faculty = faculty; }
}
