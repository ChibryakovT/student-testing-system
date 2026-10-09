package ru.edu.testing.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.ReportService;
import ru.edu.testing.service.SubjectService;
import ru.edu.testing.service.TestService;

import java.nio.charset.StandardCharsets;

/** Аналитический отчёт: доступ только у преподавателя и администратора (см. SecurityConfig). */
@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final SubjectService subjectService;
    private final TestService testService;

    public ReportController(ReportService reportService, SubjectService subjectService, TestService testService) {
        this.reportService = reportService;
        this.subjectService = subjectService;
        this.testService = testService;
    }

    @GetMapping
    public String report(@RequestParam(required = false) Long subjectId,
                         @RequestParam(required = false) Long testId,
                         @AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("summary", reportService.summary());
        model.addAttribute("rows", reportService.testStatistics(subjectId));
        model.addAttribute("subjects", subjectService.findAll(null));
        model.addAttribute("tests", testService.search(null, null, "title", me));
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("testId", testId);
        if (testId != null) {
            model.addAttribute("selectedTest", testService.get(testId, me));
            model.addAttribute("questionRows", reportService.questionStatistics(testId));
        }
        return "reports/index";
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long subjectId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test-report.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(reportService.testStatisticsCsv(subjectId).getBytes(StandardCharsets.UTF_8));
    }
}
