package ru.edu.testing.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.edu.testing.domain.*;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.repository.UserRepository;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.ScheduleService;
import ru.edu.testing.service.UniversityService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Расписание занятий и сессии. Смотреть может любой пользователь (любую группу или преподавателя),
 * изменять — только администратор (POST /schedule/** закрыт в SecurityConfig).
 */
@Controller
@RequestMapping("/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final UniversityService universityService;
    private final UserRepository userRepository;

    public ScheduleController(ScheduleService scheduleService, UniversityService universityService,
                              UserRepository userRepository) {
        this.scheduleService = scheduleService;
        this.universityService = universityService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String schedule(@RequestParam(required = false) Long groupId,
                           @RequestParam(required = false) Long teacherId,
                           @RequestParam(defaultValue = "week") String tab,
                           @RequestParam(required = false) Long editLesson,
                           @AuthenticationPrincipal AppUserDetails me, Model model) {
        List<StudyGroup> groups = universityService.groups();
        // Преподаватель видит только свои занятия и экзамены — выбор других групп и преподавателей ему недоступен
        boolean teacherOnly = me.isStaff() && !me.isAdmin();
        if (teacherOnly) {
            groupId = null;
            teacherId = me.getId();
        }
        // По умолчанию: студент — своя группа, преподаватель — свои занятия, администратор — первая группа
        if (groupId == null && teacherId == null) {
            if (me.isStudent()) {
                StudyGroup own = userRepository.findById(me.getId()).map(User::getGroup).orElse(null);
                groupId = own == null ? null : own.getId();
            } else if (!me.isAdmin()) {
                teacherId = me.getId();
            }
            if (groupId == null && teacherId == null && !groups.isEmpty()) {
                groupId = groups.get(0).getId();
            }
        }
        List<Lesson> lessons;
        List<Exam> exams;
        String heading;
        if (teacherId != null) {
            User teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new ru.edu.testing.exception.NotFoundException("Преподаватель не найден"));
            lessons = scheduleService.lessonsOfTeacher(teacherId);
            exams = scheduleService.examsOfTeacher(teacherId);
            heading = teacher.getFullName();
            groupId = null;
        } else if (groupId != null) {
            lessons = scheduleService.lessonsOfGroup(groupId);
            exams = scheduleService.examsOfGroup(groupId);
            heading = "Группа " + universityService.group(groupId).getName();
            model.addAttribute("plan", universityService.curriculum(groupId));
        } else {
            lessons = List.of();
            exams = List.of();
            heading = "Расписание";
        }
        LocalDate today = LocalDate.now();
        model.addAttribute("heading", heading);
        model.addAttribute("groupId", groupId);
        model.addAttribute("teacherId", teacherId);
        model.addAttribute("tab", "exams".equals(tab) ? "exams" : "week");
        model.addAttribute("groups", groups);
        model.addAttribute("teachers", universityService.teachers());
        model.addAttribute("grid", scheduleService.grid(lessons));
        model.addAttribute("days", ScheduleService.DAYS);
        model.addAttribute("numerator", ScheduleService.isNumerator(today));
        model.addAttribute("todayIndex", today.getDayOfWeek().getValue() - 1);
        model.addAttribute("exams", exams);
        model.addAttribute("today", today);
        model.addAttribute("byTeacher", teacherId != null);
        model.addAttribute("teacherOnly", teacherOnly);
        if (me.isAdmin()) {
            model.addAttribute("subjects", universityService.subjects());
            model.addAttribute("weekTypes", WeekType.values());
            model.addAttribute("pairTimes", ScheduleService.PAIR_TIMES);
            if (editLesson != null) {
                model.addAttribute("edit", scheduleService.lesson(editLesson));
            }
        }
        return "schedule/index";
    }

    @PostMapping("/lessons")
    public String saveLesson(@RequestParam(required = false) Long id, @RequestParam Long groupId,
                             @RequestParam int day, @RequestParam int pair,
                             @RequestParam(required = false) WeekType week,
                             @RequestParam(required = false) Long subjectId,
                             @RequestParam(required = false) String title,
                             @RequestParam(required = false) Long teacherId,
                             @RequestParam(required = false) Long coTeacherId,
                             @RequestParam(required = false) String room, RedirectAttributes redirect) {
        try {
            scheduleService.saveLesson(id, groupId, day, pair, week, subjectId, title, teacherId, coTeacherId, room);
            redirect.addFlashAttribute("success", id == null ? "Занятие добавлено" : "Занятие изменено");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/schedule?groupId=" + groupId;
    }

    @PostMapping("/lessons/{id}/delete")
    public String deleteLesson(@PathVariable Long id, RedirectAttributes redirect) {
        Long groupId = scheduleService.deleteLesson(id);
        redirect.addFlashAttribute("success", "Занятие удалено из расписания");
        return "redirect:/schedule?groupId=" + groupId;
    }

    @PostMapping("/exams")
    public String saveExam(@RequestParam Long groupId, @RequestParam Long planId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime time,
                           @RequestParam(required = false) String room, RedirectAttributes redirect) {
        try {
            scheduleService.saveExam(planId, date, time, room);
            redirect.addFlashAttribute("success", "Расписание сессии обновлено");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/schedule?tab=exams&groupId=" + groupId;
    }

    @PostMapping("/exams/{id}/delete")
    public String deleteExam(@PathVariable Long id, RedirectAttributes redirect) {
        Long groupId = scheduleService.deleteExam(id);
        redirect.addFlashAttribute("success", "Экзамен удалён из расписания сессии");
        return "redirect:/schedule?tab=exams&groupId=" + groupId;
    }
}
