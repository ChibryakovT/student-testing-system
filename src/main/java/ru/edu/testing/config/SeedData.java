package ru.edu.testing.config;

import java.util.List;

/** Формат файла начальных данных вуза (data/university.json или seed/demo-university.json). */
public record SeedData(List<FacultySeed> faculties, List<SubjectSeed> subjects, List<TeacherSeed> teachers,
                       List<GroupSeed> groups, List<PlanSeed> curriculum, List<TestSeed> tests,
                       List<LessonSeed> lessons) {

    /** Пара: день (1 — Пн), номер пары, неделя (EVERY / NUMERATOR / DENOMINATOR), дисциплина или название. */
    public record LessonSeed(String group, int day, int pair, String week, String subject, String title,
                             String teacher, String coTeacher, String room) {
    }

    public record FacultySeed(String name, List<String> departments) {
    }

    public record SubjectSeed(String name, String department, String description) {
    }

    public record TeacherSeed(String fullName, String department) {
    }

    public record GroupSeed(String name, int course, String specialty, String faculty, int admissionYear, int students) {
    }

    /** Строка учебного плана: группа, дисциплина, преподаватель, семестр, форма контроля (EXAM / CREDIT / GRADED_CREDIT). */
    public record PlanSeed(String group, String subject, String teacher, int semester, String form) {
    }

    /** Тест: вопросы в виде строк, правильные варианты отмечены «+» в конце. */
    public record TestSeed(String subject, String teacher, String title, String description, int minutes, int passing,
                           List<QuestionSeed> questions) {
    }

    public record QuestionSeed(String text, int points, List<String> options) {
    }
}
