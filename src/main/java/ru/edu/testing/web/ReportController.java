package ru.edu.testing.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import ru.edu.testing.dto.GradeSheet;
import ru.edu.testing.dto.SheetColumn;
import ru.edu.testing.dto.SheetForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.GradeSheetService;
import ru.edu.testing.service.UniversityService;
import ru.edu.testing.service.ReportService;
import ru.edu.testing.service.SubjectService;
import ru.edu.testing.service.TestService;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Аналитический отчёт: доступ только у администратора (см. SecurityConfig). */
@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final SubjectService subjectService;
    private final TestService testService;
    private final GradeSheetService sheetService;
    private final UniversityService universityService;
    private final String university;

    public ReportController(ReportService reportService, SubjectService subjectService, TestService testService,
                            GradeSheetService sheetService, UniversityService universityService,
                            @Value("${app.sheet.university:}") String university) {
        this.reportService = reportService;
        this.subjectService = subjectService;
        this.testService = testService;
        this.sheetService = sheetService;
        this.universityService = universityService;
        this.university = university;
    }

    @GetMapping
    public String report(@RequestParam(required = false) Long subjectId,
                         @RequestParam(required = false) Long rankGroupId,
                         @RequestParam(required = false) Long testId,
                         @AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("summary", reportService.summary());
        model.addAttribute("rows", reportService.testStatistics(subjectId));
        model.addAttribute("groupRows", reportService.groupStatistics(subjectId));
        model.addAttribute("ranking", reportService.ranking(subjectId, rankGroupId, 10));
        model.addAttribute("groups", universityService.groups());
        model.addAttribute("rankGroupId", rankGroupId);
        model.addAttribute("subjects", subjectService.findAll(null));
        model.addAttribute("tests", testService.search(null, null, "title", me));
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("testId", testId);
        if (testId != null) {
            model.addAttribute("selectedTest", testService.get(testId, me));
            model.addAttribute("questionRows", reportService.questionStatistics(testId));
            model.addAttribute("quality", reportService.questionQuality(testId));
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

    // ---------- Ведомость: панель настройки, печать, CSV, Excel ----------

    @GetMapping("/sheet")
    public String sheet(@ModelAttribute("form") SheetForm form, Model model) {
        sheetService.fillFromPlan(form, university);
        model.addAttribute("groups", universityService.groups());
        model.addAttribute("plan", form.getGroupId() == null ? List.of() : universityService.curriculum(form.getGroupId()));
        model.addAttribute("sheetTests", sheetService.testsOf(form.getSubjectId()));
        model.addAttribute("allColumns", SheetColumn.values());
        if (form.getGroupId() != null && form.getSubjectId() != null && form.getTestId() != null) {
            try {
                model.addAttribute("sheet", sheetService.build(form));
            } catch (BusinessException e) {
                model.addAttribute("errorMessage", e.getMessage());
            }
        }
        return "reports/sheet";
    }

    @GetMapping("/sheet/print")
    public String sheetPrint(@ModelAttribute("form") SheetForm form, Model model) {
        sheetService.fillFromPlan(form, university);
        model.addAttribute("sheet", sheetService.build(form));
        return "reports/sheet-print";
    }

    @GetMapping("/sheet/export.csv")
    public ResponseEntity<byte[]> sheetCsv(@ModelAttribute SheetForm form) {
        sheetService.fillFromPlan(form, university);
        GradeSheet sheet = sheetService.build(form);
        String charset = "windows-1251".equalsIgnoreCase(form.getEncoding()) ? "windows-1251" : "UTF-8";
        return file(sheetService.toCsv(sheet), GradeSheetService.fileName(sheet, "csv"),
                MediaType.parseMediaType("text/csv; charset=" + charset));
    }

    @GetMapping("/sheet/export.xlsx")
    public ResponseEntity<byte[]> sheetXlsx(@ModelAttribute SheetForm form) {
        sheetService.fillFromPlan(form, university);
        GradeSheet sheet = sheetService.build(form);
        return file(sheetService.toXlsx(sheet), GradeSheetService.fileName(sheet, "xlsx"),
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(type)
                .body(body);
    }
}
