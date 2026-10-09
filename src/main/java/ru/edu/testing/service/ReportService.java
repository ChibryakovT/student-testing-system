package ru.edu.testing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.AttemptStatus;
import ru.edu.testing.domain.Role;
import ru.edu.testing.dto.DashboardSummary;
import ru.edu.testing.dto.QuestionStatRow;
import ru.edu.testing.dto.TestStatRow;
import ru.edu.testing.repository.TestAttemptRepository;
import ru.edu.testing.repository.TestRepository;
import ru.edu.testing.repository.UserRepository;

import java.util.List;

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

    /**
     * Экранирование ячейки CSV. Значения, начинающиеся с = + - @, получают апостроф,
     * чтобы Excel не выполнил их как формулу (защита от CSV-инъекции).
     */
    static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
