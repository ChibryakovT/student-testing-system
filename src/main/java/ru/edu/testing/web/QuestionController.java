package ru.edu.testing.web;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.edu.testing.domain.Question;
import ru.edu.testing.domain.QuestionType;
import ru.edu.testing.domain.Test;
import ru.edu.testing.dto.QuestionForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.QuestionImportService;
import ru.edu.testing.service.QuestionService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import ru.edu.testing.service.TestService;

@Controller
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class QuestionController {

    private final QuestionService questionService;
    private final TestService testService;
    private final QuestionImportService importService;

    public QuestionController(QuestionService questionService, TestService testService,
                              QuestionImportService importService) {
        this.questionService = questionService;
        this.testService = testService;
        this.importService = importService;
    }

    @GetMapping("/tests/{testId}/questions/new")
    public String createForm(@PathVariable Long testId, @AuthenticationPrincipal AppUserDetails me, Model model) {
        Test test = testService.getForEdit(testId, me);
        model.addAttribute("form", new QuestionForm().padOptions());
        return formView(model, test, null);
    }

    @PostMapping("/tests/{testId}/questions")
    public String create(@PathVariable Long testId, @Valid @ModelAttribute("form") QuestionForm form,
                         BindingResult result, @AuthenticationPrincipal AppUserDetails me, Model model,
                         RedirectAttributes redirect) {
        Test test = testService.getForEdit(testId, me);
        if (!result.hasErrors()) {
            try {
                questionService.add(testId, form, me);
                redirect.addFlashAttribute("success", "Вопрос добавлен");
                return "redirect:/tests/" + testId;
            } catch (BusinessException e) {
                result.reject("question", e.getMessage());
            }
        }
        form.padOptions();
        return formView(model, test, null);
    }

    @GetMapping("/questions/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        Question question = questionService.getForEdit(id, me);
        model.addAttribute("form", QuestionService.toForm(question));
        return formView(model, question.getTest(), id);
    }

    @PostMapping("/questions/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") QuestionForm form,
                         BindingResult result, @AuthenticationPrincipal AppUserDetails me, Model model,
                         RedirectAttributes redirect) {
        Question question = questionService.getForEdit(id, me);
        if (!result.hasErrors()) {
            try {
                questionService.update(id, form, me);
                redirect.addFlashAttribute("success", "Вопрос обновлён");
                return "redirect:/tests/" + question.getTest().getId();
            } catch (BusinessException e) {
                result.reject("question", e.getMessage());
            }
        }
        form.padOptions();
        return formView(model, question.getTest(), id);
    }

    @PostMapping("/questions/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                         RedirectAttributes redirect) {
        Long testId = questionService.delete(id, me);
        redirect.addFlashAttribute("success", "Вопрос удалён");
        return "redirect:/tests/" + testId;
    }

    @GetMapping("/tests/{testId}/questions/template.xlsx")
    public ResponseEntity<byte[]> template(@PathVariable Long testId, @AuthenticationPrincipal AppUserDetails me) {
        testService.getForEdit(testId, me);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Шаблон_вопросов.xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(importService.template());
    }

    @PostMapping("/tests/{testId}/questions/import")
    public String importQuestions(@PathVariable Long testId, @RequestParam("file") MultipartFile file,
                                  @AuthenticationPrincipal AppUserDetails me, RedirectAttributes redirect) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (file.isEmpty() || !name.endsWith(".xlsx")) {
            redirect.addFlashAttribute("errorMessage", "Выберите файл Excel (.xlsx)");
            return "redirect:/tests/" + testId;
        }
        try (var in = file.getInputStream()) {
            int count = importService.importXlsx(testId, in, me);
            redirect.addFlashAttribute("success", "Импортировано вопросов: " + count);
        } catch (BusinessException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        } catch (java.io.IOException e) {
            redirect.addFlashAttribute("errorMessage", "Не удалось прочитать файл");
        }
        return "redirect:/tests/" + testId;
    }

    private String formView(Model model, Test test, Long questionId) {
        model.addAttribute("test", test);
        model.addAttribute("questionId", questionId);
        model.addAttribute("types", QuestionType.values());
        return "questions/form";
    }
}
