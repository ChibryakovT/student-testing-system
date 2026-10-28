package ru.edu.testing.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.edu.testing.domain.ControlForm;
import ru.edu.testing.domain.StudyGroup;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.repository.UserRepository;
import ru.edu.testing.service.UniversityService;

import java.util.LinkedHashMap;
import java.util.Map;

/** Структура вуза и учебный план. Доступ: администратор (см. SecurityConfig). */
@Controller
@RequestMapping("/structure")
public class StructureController {

    private final UniversityService universityService;
    private final UserRepository userRepository;

    public StructureController(UniversityService universityService, UserRepository userRepository) {
        this.universityService = universityService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String index(Model model) {
        Map<StudyGroup, Long> groups = new LinkedHashMap<>();
        universityService.groups().forEach(g -> groups.put(g, universityService.studentCount(g.getId())));
        model.addAttribute("groups", groups);
        model.addAttribute("faculties", universityService.faculties());
        model.addAttribute("departments", universityService.departments());
        model.addAttribute("teachers", universityService.teachers());
        return "structure/index";
    }

    @GetMapping("/groups/{id}")
    public String group(@PathVariable Long id, Model model) {
        model.addAttribute("group", universityService.group(id));
        model.addAttribute("plan", universityService.curriculum(id));
        model.addAttribute("students", userRepository.findStudentsOfGroup(id));
        model.addAttribute("subjects", universityService.subjects());
        model.addAttribute("teachers", universityService.teachers());
        model.addAttribute("controlForms", ControlForm.values());
        return "structure/group";
    }

    @GetMapping("/teachers/{id}")
    public String teacher(@PathVariable Long id, Model model) {
        model.addAttribute("teacher", userRepository.findById(id).orElseThrow(
                () -> new ru.edu.testing.exception.NotFoundException("Преподаватель не найден")));
        model.addAttribute("load", universityService.teacherLoad(id));
        return "structure/teacher";
    }

    @PostMapping("/groups")
    public String createGroup(@RequestParam String name, @RequestParam(required = false) Integer course,
                              @RequestParam(required = false) String specialty,
                              @RequestParam(required = false) Long facultyId, RedirectAttributes redirect) {
        try {
            StudyGroup g = universityService.createGroup(name, course, specialty, facultyId);
            redirect.addFlashAttribute("success", "Группа «" + g.getName() + "» создана");
            return "redirect:/structure/groups/" + g.getId();
        } catch (BusinessException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/structure";
        }
    }

    @PostMapping("/groups/{id}/plan")
    public String savePlan(@PathVariable Long id, @RequestParam Long subjectId,
                           @RequestParam(required = false) Long teacherId,
                           @RequestParam(required = false) Integer semester,
                           @RequestParam(required = false) ControlForm controlForm, RedirectAttributes redirect) {
        try {
            universityService.savePlanItem(id, subjectId, teacherId, semester, controlForm);
            redirect.addFlashAttribute("success", "Учебный план обновлён");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/structure/groups/" + id;
    }

    @PostMapping("/plan/{itemId}/delete")
    public String deletePlan(@PathVariable Long itemId, RedirectAttributes redirect) {
        Long groupId = universityService.deletePlanItem(itemId);
        redirect.addFlashAttribute("success", "Дисциплина убрана из учебного плана группы");
        return "redirect:/structure/groups/" + groupId;
    }
}
