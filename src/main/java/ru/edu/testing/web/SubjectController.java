package ru.edu.testing.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.edu.testing.domain.Subject;
import ru.edu.testing.dto.SubjectForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.service.SubjectService;

/** Справочник дисциплин. Доступ ограничен в SecurityConfig (преподаватель, администратор). */
@Controller
@RequestMapping("/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("subjects", subjectService.findAll(q));
        model.addAttribute("q", q);
        return "subjects/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new SubjectForm());
        model.addAttribute("subjectId", null);
        return "subjects/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") SubjectForm form, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        model.addAttribute("subjectId", null);
        if (result.hasErrors()) {
            return "subjects/form";
        }
        try {
            subjectService.create(form);
        } catch (BusinessException e) {
            result.reject("subject", e.getMessage());
            return "subjects/form";
        }
        redirect.addFlashAttribute("success", "Дисциплина добавлена");
        return "redirect:/subjects";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Subject subject = subjectService.get(id);
        SubjectForm form = new SubjectForm();
        form.setName(subject.getName());
        form.setDescription(subject.getDescription());
        model.addAttribute("form", form);
        model.addAttribute("subjectId", id);
        return "subjects/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") SubjectForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        model.addAttribute("subjectId", id);
        if (result.hasErrors()) {
            return "subjects/form";
        }
        try {
            subjectService.update(id, form);
        } catch (BusinessException e) {
            result.reject("subject", e.getMessage());
            return "subjects/form";
        }
        redirect.addFlashAttribute("success", "Изменения сохранены");
        return "redirect:/subjects";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            subjectService.delete(id);
            redirect.addFlashAttribute("success", "Дисциплина удалена");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/subjects";
    }
}
