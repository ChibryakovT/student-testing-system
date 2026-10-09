package ru.edu.testing.web;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.edu.testing.domain.Test;
import ru.edu.testing.domain.TestAttempt;
import ru.edu.testing.dto.TestForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.AttemptService;
import ru.edu.testing.service.SubjectService;
import ru.edu.testing.service.TestService;

@Controller
@RequestMapping("/tests")
public class TestController {

    private final TestService testService;
    private final SubjectService subjectService;
    private final AttemptService attemptService;

    public TestController(TestService testService, SubjectService subjectService, AttemptService attemptService) {
        this.testService = testService;
        this.subjectService = subjectService;
        this.attemptService = attemptService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long subjectId,
                       @RequestParam(defaultValue = "new") String sort,
                       @AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("tests", testService.search(q, subjectId, sort, me));
        model.addAttribute("subjects", subjectService.findAll(null));
        model.addAttribute("q", q);
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("sort", sort);
        return "tests/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        Test test = testService.get(id, me);
        model.addAttribute("test", test);
        model.addAttribute("canEdit", testService.canEdit(test, me));
        return "tests/view";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public String createForm(Model model) {
        model.addAttribute("form", new TestForm());
        return formView(model, null);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public String create(@Valid @ModelAttribute("form") TestForm form, BindingResult result,
                         @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return formView(model, null);
        }
        try {
            Test test = testService.create(form, me);
            redirect.addFlashAttribute("success", "Тест создан. Теперь добавьте вопросы");
            return "redirect:/tests/" + test.getId();
        } catch (BusinessException e) {
            result.reject("test", e.getMessage());
            return formView(model, null);
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        Test test = testService.getForEdit(id, me);
        TestForm form = new TestForm();
        form.setTitle(test.getTitle());
        form.setDescription(test.getDescription());
        form.setSubjectId(test.getSubject().getId());
        form.setTimeLimitMinutes(test.getTimeLimitMinutes());
        form.setPassingScore(test.getPassingScore());
        form.setPublished(test.isPublished());
        model.addAttribute("form", form);
        return formView(model, id);
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") TestForm form, BindingResult result,
                         @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return formView(model, id);
        }
        try {
            testService.update(id, form, me);
        } catch (BusinessException e) {
            result.reject("test", e.getMessage());
            return formView(model, id);
        }
        redirect.addFlashAttribute("success", "Изменения сохранены");
        return "redirect:/tests/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                         RedirectAttributes redirect) {
        testService.delete(id, me);
        redirect.addFlashAttribute("success", "Тест удалён");
        return "redirect:/tests";
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public String start(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        TestAttempt attempt = attemptService.start(id, me);
        return "redirect:/attempts/" + attempt.getId();
    }

    private String formView(Model model, Long id) {
        model.addAttribute("testId", id);
        model.addAttribute("subjects", subjectService.findAll(null));
        return "tests/form";
    }
}
