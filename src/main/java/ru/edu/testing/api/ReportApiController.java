package ru.edu.testing.api;

import org.springframework.web.bind.annotation.*;
import ru.edu.testing.dto.DashboardSummary;
import ru.edu.testing.dto.QuestionStatRow;
import ru.edu.testing.dto.TestStatRow;
import ru.edu.testing.service.ReportService;

import java.util.List;

/** Аналитика. Доступ: преподаватель и администратор (см. SecurityConfig). */
@RestController
@RequestMapping("/api/reports")
public class ReportApiController {

    private final ReportService reportService;

    public ReportApiController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return reportService.summary();
    }

    @GetMapping("/tests")
    public List<TestStatRow> tests(@RequestParam(required = false) Long subjectId) {
        return reportService.testStatistics(subjectId);
    }

    @GetMapping("/tests/{testId}/questions")
    public List<QuestionStatRow> questions(@PathVariable Long testId) {
        return reportService.questionStatistics(testId);
    }
}
