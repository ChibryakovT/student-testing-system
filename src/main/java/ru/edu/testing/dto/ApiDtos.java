package ru.edu.testing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ru.edu.testing.domain.*;

import java.time.LocalDateTime;
import java.util.List;

/** DTO для REST API. Сущности JPA наружу не отдаются. */
public final class ApiDtos {

    private ApiDtos() {
    }

    public record UserDto(Long id, String username, String fullName, String email, Role role,
                          String groupName, String recordBook, boolean enabled) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole(),
                    u.getGroupName(), u.getRecordBook(), u.isEnabled());
        }
    }

    public record SubjectDto(Long id, String name, String description, String department) {
        public static SubjectDto of(Subject s) {
            return new SubjectDto(s.getId(), s.getName(), s.getDescription(),
                    s.getDepartment() == null ? null : s.getDepartment().getName());
        }
    }

    public record TestDto(Long id, String title, String description, Long subjectId, String subject,
                          String author, int timeLimitMinutes, int passingScore, boolean published,
                          int questionCount, int maxScore, LocalDateTime createdAt) {
        public static TestDto of(Test t) {
            return new TestDto(t.getId(), t.getTitle(), t.getDescription(), t.getSubject().getId(),
                    t.getSubject().getName(), t.getAuthor().getFullName(), t.getTimeLimitMinutes(),
                    t.getPassingScore(), t.isPublished(), t.getQuestions().size(), t.getMaxScore(),
                    t.getCreatedAt());
        }
    }

    /** Вариант ответа. Поле correct заполняется только для преподавателя. */
    public record OptionDto(Long id, String text, Boolean correct) {
        public static OptionDto of(AnswerOption o, boolean withCorrect) {
            return new OptionDto(o.getId(), o.getText(), withCorrect ? o.isCorrect() : null);
        }
    }

    public record QuestionDto(Long id, String text, QuestionType type, int points, List<OptionDto> options) {
        public static QuestionDto of(Question q, boolean withCorrect) {
            return new QuestionDto(q.getId(), q.getText(), q.getType(), q.getPoints(),
                    q.getOptions().stream().map(o -> OptionDto.of(o, withCorrect)).toList());
        }
    }

    public record AttemptDto(Long id, Long testId, String testTitle, String student, String groupName,
                             String status, LocalDateTime startedAt, LocalDateTime finishedAt,
                             int score, int maxScore, int percent, boolean passed) {
        public static AttemptDto of(TestAttempt a) {
            return new AttemptDto(a.getId(), a.getTest().getId(), a.getTest().getTitle(),
                    a.getStudent().getFullName(), a.getStudent().getGroupName(), a.getStatus().name(),
                    a.getStartedAt(), a.getFinishedAt(), a.getScore(), a.getMaxScore(), a.getPercent(),
                    a.isPassed());
        }
    }

    public record StartedAttemptDto(Long attemptId, String testTitle, int timeLimitMinutes,
                                    LocalDateTime startedAt, List<QuestionDto> questions) {
    }

    public record AnswerRequest(@NotNull Long questionId, List<Long> optionIds) {
    }

    public record SubmitRequest(@NotNull @Valid List<AnswerRequest> answers) {
    }

    public record GroupDto(Long id, String name, Integer course, String specialty, String faculty) {
        public static GroupDto of(StudyGroup g) {
            return new GroupDto(g.getId(), g.getName(), g.getCourse(), g.getSpecialty(),
                    g.getFaculty() == null ? null : g.getFaculty().getName());
        }
    }

    public record CurriculumDto(Long id, Long subjectId, String subject, Long teacherId, String teacher,
                                Integer semester, ControlForm controlForm) {
        public static CurriculumDto of(CurriculumItem c) {
            return new CurriculumDto(c.getId(), c.getSubject().getId(), c.getSubject().getName(),
                    c.getTeacher() == null ? null : c.getTeacher().getId(),
                    c.getTeacher() == null ? null : c.getTeacher().getFullName(), c.getSemester(), c.getControlForm());
        }
    }

    public record CurriculumRequest(@NotNull Long subjectId, Long teacherId, Integer semester, ControlForm controlForm) {
    }

    public record GroupRequest(@jakarta.validation.constraints.NotBlank String name, Integer course, String specialty,
                               Long facultyId) {
    }

    public record ErrorResponse(LocalDateTime timestamp, int status, String error, String message,
                                String path, List<String> details) {
    }
}
