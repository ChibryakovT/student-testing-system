package ru.edu.testing.api;

import org.springframework.web.bind.annotation.*;
import ru.edu.testing.domain.Exam;
import ru.edu.testing.domain.Lesson;
import ru.edu.testing.service.ScheduleService;

import java.util.List;
import java.util.Map;

/** Расписание группы в JSON (для мобильных клиентов и Postman). */
@RestController
@RequestMapping("/api/schedule")
public class ScheduleApiController {

    public record LessonDto(Long id, int day, int pair, String time, String week, String name, String teachers, String room) {
        static LessonDto of(Lesson l) {
            return new LessonDto(l.getId(), l.getDayOfWeek(), l.getPairNo(), ScheduleService.PAIR_TIMES.get(l.getPairNo()),
                    l.getWeekType().name(), l.getName(), l.getTeachers(), l.getRoom());
        }
    }

    public record ExamDto(Long id, String date, String time, String subject, String controlForm, String teacher, String room) {
        static ExamDto of(Exam e) {
            return new ExamDto(e.getId(), e.getDate().toString(), e.getStartTime() == null ? null : e.getStartTime().toString(),
                    e.getPlan().getSubject().getName(), e.getPlan().getControlForm().getTitle(),
                    e.getPlan().getTeacher() == null ? null : e.getPlan().getTeacher().getFullName(), e.getRoom());
        }
    }

    private final ScheduleService scheduleService;

    public ScheduleApiController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/groups/{groupId}")
    public Map<String, Object> group(@PathVariable Long groupId) {
        return Map.of("lessons", scheduleService.lessonsOfGroup(groupId).stream().map(LessonDto::of).toList(),
                "exams", scheduleService.examsOfGroup(groupId).stream().map(ExamDto::of).toList());
    }

    @GetMapping("/teachers/{teacherId}")
    public Map<String, List<?>> teacher(@PathVariable Long teacherId) {
        return Map.of("lessons", scheduleService.lessonsOfTeacher(teacherId).stream().map(LessonDto::of).toList(),
                "exams", scheduleService.examsOfTeacher(teacherId).stream().map(ExamDto::of).toList());
    }
}
