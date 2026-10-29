package ru.edu.testing.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.*;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.repository.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Расписание занятий (числитель / знаменатель) и расписание сессии. */
@Service
@Transactional(readOnly = true)
public class ScheduleService {

    /** Время пар — как в расписании РТСУ. */
    public static final Map<Integer, String> PAIR_TIMES = Map.of(
            1, "12:40–14:00", 2, "14:10–15:30", 3, "15:40–17:00", 4, "11:00–12:20");
    public static final List<String> DAYS = List.of("Понедельник", "Вторник", "Среда", "Четверг", "Пятница");
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI"};

    /** Строка сетки: пара и занятия по дням (индекс 0 — понедельник). */
    public record PairRow(int pair, String roman, String time, List<List<Lesson>> days) {
    }

    private final AuditService auditService;
    private final LessonRepository lessonRepository;
    private final ExamRepository examRepository;
    private final StudyGroupRepository groupRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final CurriculumRepository curriculumRepository;

    public ScheduleService(LessonRepository lessonRepository, ExamRepository examRepository,
                           StudyGroupRepository groupRepository, SubjectRepository subjectRepository,
                           UserRepository userRepository, CurriculumRepository curriculumRepository, AuditService auditService) {
        this.auditService = auditService;
        this.lessonRepository = lessonRepository;
        this.examRepository = examRepository;
        this.groupRepository = groupRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.curriculumRepository = curriculumRepository;
    }

    public List<Lesson> lessonsOfGroup(Long groupId) {
        return lessonRepository.findByGroup(groupId);
    }

    public List<Lesson> lessonsOfTeacher(Long teacherId) {
        return lessonRepository.findByTeacher(teacherId);
    }

    public List<Exam> examsOfGroup(Long groupId) {
        return examRepository.findByGroup(groupId);
    }

    public List<Exam> examsOfTeacher(Long teacherId) {
        return examRepository.findByTeacher(teacherId);
    }

    /** Сетка недели: строки — пары I–IV, столбцы — дни Пн–Пт. */
    public List<PairRow> grid(List<Lesson> lessons) {
        List<PairRow> rows = new ArrayList<>();
        for (int pair = 1; pair <= 4; pair++) {
            List<List<Lesson>> days = new ArrayList<>();
            for (int day = 1; day <= DAYS.size(); day++) {
                int p = pair, d = day;
                days.add(lessons.stream().filter(l -> l.getPairNo() == p && l.getDayOfWeek() == d).toList());
            }
            rows.add(new PairRow(pair, ROMAN[pair], PAIR_TIMES.get(pair), days));
        }
        return rows;
    }

    /**
     * Числитель — нечётная учебная неделя. Отсчёт с недели, в которую попадает 1 сентября текущего учебного года.
     */
    public static boolean isNumerator(LocalDate date) {
        int year = date.getMonthValue() >= 9 ? date.getYear() : date.getYear() - 1;
        LocalDate start = LocalDate.of(year, 9, 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long week = ChronoUnit.WEEKS.between(start, date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) + 1;
        return week % 2 == 1;
    }

    /** Занятия на сегодня с учётом числителя / знаменателя, по порядку начала пары. */
    public List<Lesson> today(List<Lesson> lessons) {
        LocalDate now = LocalDate.now();
        boolean numerator = isNumerator(now);
        int day = now.getDayOfWeek().getValue();
        return lessons.stream()
                .filter(l -> l.getDayOfWeek() == day && l.getWeekType().matches(numerator))
                .sorted(Comparator.comparing(l -> PAIR_TIMES.getOrDefault(l.getPairNo(), "")))
                .toList();
    }

    /** Дней до даты (отрицательное — уже прошла). */
    public long daysUntil(LocalDate date) {
        return ChronoUnit.DAYS.between(LocalDate.now(), date);
    }

    public List<Exam> upcoming(List<Exam> exams, int limit) {
        LocalDate today = LocalDate.now();
        return exams.stream().filter(e -> !e.getDate().isBefore(today)).limit(limit).toList();
    }

    // ---------- Изменение (администратор) ----------

    @Transactional
    public Lesson saveLesson(Long id, Long groupId, int day, int pair, WeekType week, Long subjectId, String title,
                             Long teacherId, Long coTeacherId, String room) {
        if (day < 1 || day > DAYS.size()) {
            throw new BusinessException("День недели — с понедельника по пятницу");
        }
        if (!PAIR_TIMES.containsKey(pair)) {
            throw new BusinessException("Номер пары — от I до IV");
        }
        if (subjectId == null && (title == null || title.isBlank())) {
            throw new BusinessException("Выберите дисциплину или укажите название занятия");
        }
        Lesson lesson = id == null ? new Lesson()
                : lessonRepository.findById(id).orElseThrow(() -> new NotFoundException("Занятие не найдено"));
        lesson.setGroup(groupRepository.findById(groupId).orElseThrow(() -> new BusinessException("Группа не найдена")));
        lesson.setDayOfWeek(day);
        lesson.setPairNo(pair);
        lesson.setWeekType(week == null ? WeekType.EVERY : week);
        lesson.setSubject(subjectId == null ? null : subjectRepository.findById(subjectId)
                .orElseThrow(() -> new BusinessException("Дисциплина не найдена")));
        lesson.setTitle(subjectId == null ? title.trim() : null);
        lesson.setTeacher(teacher(teacherId));
        lesson.setCoTeacher(teacher(coTeacherId));
        lesson.setRoom(blankToNull(room));
        auditService.log(id == null ? "Занятие добавлено" : "Занятие изменено", lesson.getGroup().getName() + ", "
                + DAYS.get(day - 1).toLowerCase() + ", " + ROMAN[pair] + " пара: " + lesson.getName()
                + (lesson.getRoom() == null ? "" : ", ауд. " + lesson.getRoom()));
        return lessonRepository.save(lesson);
    }

    @Transactional
    public Long deleteLesson(Long id) {
        Lesson lesson = lessonRepository.findById(id).orElseThrow(() -> new NotFoundException("Занятие не найдено"));
        Long groupId = lesson.getGroup().getId();
        auditService.log("Занятие удалено", lesson.getGroup().getName() + ", " + DAYS.get(lesson.getDayOfWeek() - 1).toLowerCase()
                + ", " + ROMAN[lesson.getPairNo()] + " пара: " + lesson.getName());
        lessonRepository.delete(lesson);
        return groupId;
    }

    /** Экзамен / зачёт по строке учебного плана. Если для неё уже назначена дата — она переносится. */
    @Transactional
    public Exam saveExam(Long planId, LocalDate date, LocalTime time, String room) {
        if (date == null) {
            throw new BusinessException("Укажите дату");
        }
        CurriculumItem plan = curriculumRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("Дисциплина не найдена в учебном плане группы"));
        Exam exam = examRepository.findByPlanId(planId).orElseGet(Exam::new);
        exam.setPlan(plan);
        exam.setDate(date);
        exam.setStartTime(time);
        exam.setRoom(blankToNull(room));
        auditService.log("Сессия изменена", plan.getGroup().getName() + ": " + plan.getSubject().getName() + " ("
                + plan.getControlForm().getTitle() + ") — " + date + (time == null ? "" : " " + time));
        return examRepository.save(exam);
    }

    @Transactional
    public Long deleteExam(Long id) {
        Exam exam = examRepository.findById(id).orElseThrow(() -> new NotFoundException("Экзамен не найден"));
        Long groupId = exam.getPlan().getGroup().getId();
        auditService.log("Сессия изменена", exam.getPlan().getGroup().getName() + ": удалён " + exam.getPlan().getSubject().getName());
        examRepository.delete(exam);
        return groupId;
    }

    public Lesson lesson(Long id) {
        return lessonRepository.findById(id).orElseThrow(() -> new NotFoundException("Занятие не найдено"));
    }

    private User teacher(Long id) {
        if (id == null) {
            return null;
        }
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException("Преподаватель не найден"));
        if (u.getRole() == Role.STUDENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Вести занятие может только преподаватель");
        }
        return u;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
