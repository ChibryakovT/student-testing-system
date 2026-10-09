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
import ru.edu.testing.service.QuestionService;
import ru.edu.testing.service.TestService;

@Controller
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class QuestionController {

    private final QuestionService questionService;
    private final TestService testService;

    public QuestionController(QuestionService questionService, TestService testService) {
        this.questionService = questionService;
        this.testService = testService;
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

    private String formView(Model model, Test test, Long questionId) {
        model.addAttribute("test", test);
        model.addAttribute("questionId", questionId);
        model.addAttribute("types", QuestionType.values());
        return "questions/form";
    }
}
