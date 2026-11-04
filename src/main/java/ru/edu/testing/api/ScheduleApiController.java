package ru.edu.testing.api;

import org.springframework.web.bind.annotation.*;
import ru.edu.testing.domain.Exam;
import ru.edu.testing.domain.Lesson;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ru.edu.testing.domain.User;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.repository.StudyGroupRepository;
import ru.edu.testing.repository.UserRepository;
import ru.edu.testing.security.AppUserDetails;
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
    private final UserRepository userRepository;
    private final StudyGroupRepository groupRepository;

    public ScheduleApiController(ScheduleService scheduleService, UserRepository userRepository,
                                 StudyGroupRepository groupRepository) {
        this.scheduleService = scheduleService;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
    }

    /** Студент — только своя группа, преподаватель — только группы, у которых он ведёт, администратор — любая. */
    @GetMapping("/groups/{groupId}")
    public Map<String, Object> group(@PathVariable Long groupId, @AuthenticationPrincipal AppUserDetails me) {
        boolean allowed = me.isAdmin()
                || (me.isStudent() && userRepository.findById(me.getId()).map(User::getGroup)
                        .map(g -> g.getId().equals(groupId)).orElse(false))
                || (me.isStaff() && groupRepository.findTaughtBy(me.getId()).stream().anyMatch(g -> g.getId().equals(groupId)));
        if (!allowed) {
            throw new NotFoundException("Расписание не найдено");
        }
        return Map.of("lessons", scheduleService.lessonsOfGroup(groupId).stream().map(LessonDto::of).toList(),
                "exams", scheduleService.examsOfGroup(groupId).stream().map(ExamDto::of).toList());
    }

    /** Расписание преподавателя: сам преподаватель или администратор. */
    @GetMapping("/teachers/{teacherId}")
    public Map<String, List<?>> teacher(@PathVariable Long teacherId, @AuthenticationPrincipal AppUserDetails me) {
        if (!me.isAdmin() && !me.getId().equals(teacherId)) {
            throw new NotFoundException("Расписание не найдено");
        }
        return Map.of("lessons", scheduleService.lessonsOfTeacher(teacherId).stream().map(LessonDto::of).toList(),
                "exams", scheduleService.examsOfTeacher(teacherId).stream().map(ExamDto::of).toList());
    }
}
