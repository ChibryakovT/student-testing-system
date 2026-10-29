package ru.edu.testing.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.edu.testing.domain.*;
import ru.edu.testing.repository.UserRepository;
import ru.edu.testing.service.ScheduleService;
import ru.edu.testing.dto.DashboardSummary;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.AttemptService;
import ru.edu.testing.service.ReportService;
import ru.edu.testing.service.TestService;
import ru.edu.testing.service.UniversityService;

import java.util.List;
import java.util.Map;

/** Главная страница: у каждой роли — свои главные действия, без дублирования меню. */
@Controller
public class HomeController {

    private final ReportService reportService;
    private final TestService testService;
    private final AttemptService attemptService;
    private final UniversityService universityService;
    private final ScheduleService scheduleService;
    private final UserRepository userRepository;

    public HomeController(ReportService reportService, TestService testService, AttemptService attemptService,
                          UniversityService universityService, ScheduleService scheduleService,
                          UserRepository userRepository) {
        this.scheduleService = scheduleService;
        this.userRepository = userRepository;
        this.reportService = reportService;
        this.testService = testService;
        this.attemptService = attemptService;
        this.universityService = universityService;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AppUserDetails me, Model model) {
        List<TestAttempt> attempts = attemptService.search(null, null, null, null, null, "date", me);
        List<Test> tests = testService.search(null, null, "new", me);
        model.addAttribute("recentAttempts", attempts.stream().limit(6).toList());

        // Сегодня по расписанию и ближайшая сессия: студент — своя группа, преподаватель — свои занятия
        if (!me.isAdmin()) {
            List<Lesson> lessons;
            List<Exam> exams;
            if (me.isStudent()) {
                Long groupId = userRepository.findById(me.getId()).map(User::getGroup).map(StudyGroup::getId).orElse(null);
                lessons = groupId == null ? List.of() : scheduleService.lessonsOfGroup(groupId);
                exams = groupId == null ? List.of() : scheduleService.examsOfGroup(groupId);
            } else {
                lessons = scheduleService.lessonsOfTeacher(me.getId());
                exams = scheduleService.examsOfTeacher(me.getId());
            }
            model.addAttribute("todayLessons", scheduleService.today(lessons));
            model.addAttribute("nextExams", scheduleService.upcoming(exams, 3));
            model.addAttribute("pairTimes", ScheduleService.PAIR_TIMES);
        }

        if (me.isStudent()) {
            // Лучший результат по каждому тесту — статус на карточке («пройден на 83 %» / «не проходил»)
            Map<Long, Integer> best = TestStatus.bestPercent(attempts);
            model.addAttribute("tests", tests);
            model.addAttribute("best", best);
            model.addAttribute("forms", testService.controlForms(me));
            model.addAttribute("pending", tests.stream().filter(t -> !best.containsKey(t.getId())).count());
            model.addAttribute("summary", stats(attempts));
        } else if (me.isAdmin()) {
            model.addAttribute("summary", reportService.summary());
            model.addAttribute("groupCount", universityService.groups().size());
            model.addAttribute("teacherCount", universityService.teachers().size());
        } else {
            // Преподаватель: статистика и нагрузка только по своим группам и дисциплинам
            model.addAttribute("summary", stats(attempts));
            model.addAttribute("load", universityService.teacherLoad(me.getId()));
            model.addAttribute("testCount", tests.size());
        }
        return "home";
    }

    private static DashboardSummary stats(List<TestAttempt> attempts) {
        long passed = attempts.stream().filter(TestAttempt::isPassed).count();
        double avg = attempts.stream().mapToInt(TestAttempt::getPercent).average().orElse(0);
        return new DashboardSummary(0, 0, 0, 0, attempts.size(), passed, Math.round(avg * 10) / 10.0);
    }
}
