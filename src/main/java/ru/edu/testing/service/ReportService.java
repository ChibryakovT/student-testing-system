package ru.edu.testing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.AttemptStatus;
import ru.edu.testing.domain.Role;
import ru.edu.testing.dto.DashboardSummary;
import ru.edu.testing.domain.AttemptAnswer;
import ru.edu.testing.domain.TestAttempt;
import ru.edu.testing.dto.QuestionQuality;
import ru.edu.testing.dto.QuestionStatRow;
import ru.edu.testing.dto.TestStatRow;
import ru.edu.testing.repository.TestAttemptRepository;
import ru.edu.testing.repository.TestRepository;
import ru.edu.testing.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Аналитический отчёт по результатам тестирования. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final UserRepository userRepository;
    private final TestRepository testRepository;
    private final TestAttemptRepository attemptRepository;

    public ReportService(UserRepository userRepository, TestRepository testRepository,
                         TestAttemptRepository attemptRepository) {
        this.userRepository = userRepository;
        this.testRepository = testRepository;
        this.attemptRepository = attemptRepository;
    }

    public DashboardSummary summary() {
        return new DashboardSummary(
                userRepository.countByRole(Role.STUDENT),
                userRepository.countByRole(Role.TEACHER),
                testRepository.count(),
                testRepository.countByPublishedTrue(),
                attemptRepository.countByStatus(AttemptStatus.FINISHED),
                attemptRepository.countByStatusAndPassedTrue(AttemptStatus.FINISHED),
                Math.round(attemptRepository.averagePercent() * 10) / 10.0);
    }

    public List<TestStatRow> testStatistics(Long subjectId) {
        return attemptRepository.testStatistics(subjectId);
    }

    public List<QuestionStatRow> questionStatistics(Long testId) {
        return attemptRepository.questionStatistics(testId);
    }

    /** Минимум студентов, при котором оценка качества вопроса имеет смысл. */
    static final int MIN_STUDENTS = 5;

    /**
     * Качество вопросов теста по первой попытке каждого студента. Различающая способность — корреляция
     * балла за вопрос с баллом за остальные вопросы (как индекс дискриминации в Moodle): хорошие вопросы
     * чаще решают те, кто хорошо написал тест в целом.
     */
    public Map<Long, QuestionQuality> questionQuality(Long testId) {
        Map<Long, TestAttempt> first = new HashMap<>();
        for (TestAttempt a : attemptRepository.findByTestIdAndStatus(testId, AttemptStatus.FINISHED)) {
            first.merge(a.getStudent().getId(), a, (x, y) -> x.getStartedAt().isBefore(y.getStartedAt()) ? x : y);
        }
        Map<Long, List<double[]>> byQuestion = new HashMap<>();
        for (TestAttempt a : first.values()) {
            int total = a.getAnswers().stream().mapToInt(AttemptAnswer::getPointsEarned).sum();
            for (AttemptAnswer ans : a.getAnswers()) {
                double share = (double) ans.getPointsEarned() / Math.max(1, ans.getQuestion().getPoints());
                byQuestion.computeIfAbsent(ans.getQuestion().getId(), k -> new ArrayList<>())
                        .add(new double[]{share, total - ans.getPointsEarned()});
            }
        }
        Map<Long, QuestionQuality> result = new HashMap<>();
        byQuestion.forEach((id, rows) -> result.put(id, quality(id, rows)));
        return result;
    }

    static QuestionQuality quality(Long id, List<double[]> rows) {
        int n = rows.size();
        double mx = rows.stream().mapToDouble(r -> r[0]).average().orElse(0);
        double my = rows.stream().mapToDouble(r -> r[1]).average().orElse(0);
        int facility = (int) Math.round(mx * 100);
        if (n < MIN_STUDENTS) {
            return new QuestionQuality(id, n, facility, null, "na", "Мало данных: нужно хотя бы " + MIN_STUDENTS + " студентов");
        }
        double sxy = 0, sxx = 0, syy = 0;
        for (double[] r : rows) {
            sxy += (r[0] - mx) * (r[1] - my);
            sxx += (r[0] - mx) * (r[0] - mx);
            syy += (r[1] - my) * (r[1] - my);
        }
        if (sxx == 0) {
            return facility >= 100
                    ? new QuestionQuality(id, n, facility, null, "warn", "Все отвечают верно: вопрос ничего не проверяет")
                    : new QuestionQuality(id, n, facility, null, "bad", "Никто не ответил верно: проверьте правильный ответ");
        }
        if (syy == 0) {
            return new QuestionQuality(id, n, facility, null, "na", "Остальные вопросы все решили одинаково");
        }
        double r = Math.round(sxy / Math.sqrt(sxx * syy) * 100) / 100.0;
        if (r < 0) {
            return new QuestionQuality(id, n, facility, r, "bad", "Нужно исправить: сильные студенты ошибаются чаще слабых");
        }
        if (r < 0.2) {
            return new QuestionQuality(id, n, facility, r, "bad", "Нужно исправить: плохо отделяет знающих от незнающих");
        }
        if (facility >= 95) {
            return new QuestionQuality(id, n, facility, r, "warn", "Слишком лёгкий");
        }
        if (facility <= 10) {
            return new QuestionQuality(id, n, facility, r, "warn", "Очень сложный: проверьте формулировку");
        }
        if (r < 0.3) {
            return new QuestionQuality(id, n, facility, r, "warn", "Приемлемо, можно улучшить");
        }
        return new QuestionQuality(id, n, facility, r, "good", "Хорошо различает");
    }

    /** Сравнение групп по среднему результату (по всем тестам или по дисциплине). */
    public List<ru.edu.testing.dto.GroupStatRow> groupStatistics(Long subjectId) {
        return attemptRepository.groupStatistics(subjectId);
    }

    /** Топ студентов по среднему результату; учитываются студенты, прошедшие хотя бы 2 теста (или 1 — в разрезе дисциплины). */
    public List<ru.edu.testing.dto.StudentRankRow> ranking(Long subjectId, Long groupId, int limit) {
        return attemptRepository.ranking(subjectId, groupId, subjectId == null ? 2 : 1,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /** Выгрузка отчёта в CSV (разделитель «;» — открывается в Excel без настройки). */
    public String testStatisticsCsv(Long subjectId) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Тест;Дисциплина;Попыток;Средний %;Мин %;Макс %;Сдали;Доля сдавших %\n");
        for (TestStatRow row : testStatistics(subjectId)) {
            sb.append(csv(row.title())).append(';')
                    .append(csv(row.subject())).append(';')
                    .append(row.attempts()).append(';')
                    .append(row.averagePercent()).append(';')
                    .append(row.minPercent()).append(';')
                    .append(row.maxPercent()).append(';')
                    .append(row.passedCount()).append(';')
                    .append(row.passRate()).append('\n');
        }
        return sb.toString();
    }

    /** Экранирование ячейки CSV с разделителем «;». */
    static String csv(String value) {
        return CsvUtil.escape(value, ';');
    }
}
