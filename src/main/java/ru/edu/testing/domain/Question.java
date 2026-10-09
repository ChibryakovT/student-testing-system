package ru.edu.testing.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id")
    private Test test;

    @Column(nullable = false, length = 1000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @Column(nullable = false)
    private int points = 1;

    @Column(nullable = false)
    private int position;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<AnswerOption> options = new ArrayList<>();

    public void addOption(AnswerOption option) {
        option.setQuestion(this);
        option.setPosition(options.size());
        options.add(option);
    }

    public Long getId() { return id; }
    public Test getTest() { return test; }
    public void setTest(Test test) { this.test = test; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public QuestionType getType() { return type; }
    public void setType(QuestionType type) { this.type = type; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public List<AnswerOption> getOptions() { return options; }
}
