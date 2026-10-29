package ru.edu.testing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.edu.testing.config.SeedData.*;
import ru.edu.testing.domain.*;
import ru.edu.testing.dto.OptionForm;
import ru.edu.testing.dto.QuestionForm;
import ru.edu.testing.dto.TestForm;
import ru.edu.testing.repository.*;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.AttemptService;
import ru.edu.testing.service.QuestionService;
import ru.edu.testing.service.TestService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Заполнение базы данными вуза: факультеты, кафедры, дисциплины, преподаватели, группы со студентами,
 * учебный план, тесты и история прохождения. Источник — локальный файл data/university.json
 * (не хранится в Git), а при его отсутствии — демонстрационный seed/demo-university.json.
 */
@Component
public class UniversitySeeder {

    private static final Logger log = LoggerFactory.getLogger(UniversitySeeder.class);

    private final ObjectMapper mapper;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final SubjectRepository subjectRepository;
    private final StudyGroupRepository groupRepository;
    private final CurriculumRepository curriculumRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TestService testService;
    private final QuestionService questionService;
    private final AttemptService attemptService;
    private final LessonRepository lessonRepository;
    private final ExamRepository examRepository;

    public UniversitySeeder(ObjectMapper mapper, FacultyRepository facultyRepository,
                            DepartmentRepository departmentRepository, SubjectRepository subjectRepository,
                            StudyGroupRepository groupRepository, CurriculumRepository curriculumRepository,
                            UserRepository userRepository, PasswordEncoder passwordEncoder, TestService testService,
                            QuestionService questionService, AttemptService attemptService,
                            LessonRepository lessonRepository, ExamRepository examRepository) {
        this.lessonRepository = lessonRepository;
        this.examRepository = examRepository;
        this.mapper = mapper;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.subjectRepository = subjectRepository;
        this.groupRepository = groupRepository;
        this.curriculumRepository = curriculumRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.testService = testService;
        this.questionService = questionService;
        this.attemptService = attemptService;
    }

    public SeedData load(String localFile) throws IOException {
        Path local = Path.of(localFile);
        if (Files.isRegularFile(local)) {
            log.info("Начальные данные вуза: {}", local.toAbsolutePath());
            try (InputStream in = Files.newInputStream(local)) {
                return mapper.readValue(in, SeedData.class);
            }
        }
        log.info("Файл {} не найден — загружаются демонстрационные данные", localFile);
        try (InputStream in = new ClassPathResource("seed/demo-university.json").getInputStream()) {
            return mapper.readValue(in, SeedData.class);
        }
    }

    /** Возвращает список созданных учётных записей (для файла с логинами). */
    public List<String[]> seed(SeedData data, String studentPassword, String teacherPassword) {
        List<String[]> accounts = new ArrayList<>();
        Map<String, Faculty> faculties = new HashMap<>();
        Map<String, Department> departments = new HashMap<>();
        for (FacultySeed f : data.faculties()) {
            Faculty faculty = facultyRepository.save(new Faculty(f.name()));
            faculties.put(f.name(), faculty);
            f.departments().forEach(d -> departments.put(d, departmentRepository.save(new Department(d, faculty))));
        }

        Map<String, Subject> subjects = new HashMap<>();
        for (SubjectSeed s : data.subjects()) {
            Subject subject = new Subject();
            subject.setName(s.name());
            subject.setDescription(s.description());
            subject.setDepartment(required(departments, s.department(), "кафедра"));
            subjects.put(s.name(), subjectRepository.save(subject));
        }

        Set<String> logins = new HashSet<>();
        String teacherHash = passwordEncoder.encode(teacherPassword);
        Map<String, User> teachers = new HashMap<>();
        for (TeacherSeed t : data.teachers()) {
            User u = newUser(t.fullName(), Role.TEACHER, teacherHash, logins);
            u.setDepartment(required(departments, t.department(), "кафедра"));
            teachers.put(t.fullName(), userRepository.save(u));
            accounts.add(new String[]{"Преподаватель", t.fullName(), u.getUsername(), t.department()});
        }

        String studentHash = passwordEncoder.encode(studentPassword);
        NameGenerator names = new NameGenerator(2026);
        Map<String, StudyGroup> groups = new LinkedHashMap<>();
        Map<String, List<User>> studentsByGroup = new HashMap<>();
        for (GroupSeed g : data.groups()) {
            StudyGroup group = groupRepository.save(new StudyGroup(g.name(), g.course(), g.specialty(),
                    required(faculties, g.faculty(), "факультет")));
            groups.put(g.name(), group);
            List<User> students = new ArrayList<>();
            for (int i = 1; i <= g.students(); i++) {
                User u = newUser(names.next(), Role.STUDENT, studentHash, logins);
                u.setGroup(group);
                u.setRecordBook(String.format("%d05%04d", g.admissionYear(), group.getId() * 100 + i));
                students.add(userRepository.save(u));
                accounts.add(new String[]{"Студент", u.getFullName(), u.getUsername(), g.name()});
            }
            studentsByGroup.put(g.name(), students);
        }

        List<CurriculumItem> plan = new ArrayList<>();
        for (PlanSeed p : data.curriculum()) {
            plan.add(curriculumRepository.save(new CurriculumItem(required(groups, p.group(), "группа"),
                    required(subjects, p.subject(), "дисциплина"),
                    p.teacher() == null ? null : required(teachers, p.teacher(), "преподаватель"),
                    p.semester(), ControlForm.valueOf(p.form()))));
        }

        int lessonCount = 0;
        for (LessonSeed l : data.lessons() == null ? List.<LessonSeed>of() : data.lessons()) {
            Lesson lesson = new Lesson();
            lesson.setGroup(required(groups, l.group(), "группа"));
            lesson.setDayOfWeek(l.day());
            lesson.setPairNo(l.pair());
            lesson.setWeekType(WeekType.valueOf(l.week()));
            lesson.setSubject(l.subject() == null ? null : required(subjects, l.subject(), "дисциплина"));
            lesson.setTitle(l.title());
            lesson.setTeacher(l.teacher() == null ? null : required(teachers, l.teacher(), "преподаватель"));
            lesson.setCoTeacher(l.coTeacher() == null ? null : required(teachers, l.coTeacher(), "преподаватель"));
            lesson.setRoom(l.room());
            lessonRepository.save(lesson);
            lessonCount++;
        }
        int examCount = scheduleSession(plan);

        Map<String, List<Test>> testsBySubject = new HashMap<>();
        for (TestSeed t : data.tests()) {
            User author = required(teachers, t.teacher(), "преподаватель");
            AppUserDetails me = new AppUserDetails(author, false);
            TestForm form = new TestForm();
            form.setTitle(t.title());
            form.setDescription(t.description());
            form.setSubjectId(required(subjects, t.subject(), "дисциплина").getId());
            form.setTimeLimitMinutes(t.minutes());
            form.setPassingScore(t.passing());
            Test test = testService.create(form, me);
            for (QuestionSeed q : t.questions()) {
                questionService.add(test.getId(), question(q), me);
            }
            test.setPublished(true);
            testsBySubject.computeIfAbsent(t.subject(), k -> new ArrayList<>()).add(test);
        }

        int attempts = simulateAttempts(data, groups, studentsByGroup, testsBySubject);
        log.info("Расписание: {} занятий, {} экзаменов и зачётов в сессию", lessonCount, examCount);
        log.info("Загружено: {} факультетов, {} кафедр, {} дисциплин, {} преподавателей, {} групп, {} студентов, "
                        + "{} строк учебного плана, {} тестов, {} попыток",
                faculties.size(), departments.size(), subjects.size(), teachers.size(), groups.size(),
                studentsByGroup.values().stream().mapToInt(List::size).sum(), data.curriculum().size(),
                data.tests().size(), attempts);
        return accounts;
    }

    /**
     * Сессия зимнего семестра: зачёты и дифференцированные зачёты — на зачётной неделе (последние будни декабря),
     * экзамены — в январе с интервалом 3–4 дня. Аудитория — та, где группа чаще всего занимается.
     */
    private int scheduleSession(List<CurriculumItem> plan) {
        int year = LocalDate.now().getMonthValue() >= 9 ? LocalDate.now().getYear() : LocalDate.now().getYear() - 1;
        String[] rooms = {"213", "214", "218", "221", "223", "224", "226"};
        Map<Long, List<CurriculumItem>> byGroup = new LinkedHashMap<>();
        plan.forEach(c -> byGroup.computeIfAbsent(c.getGroup().getId(), k -> new ArrayList<>()).add(c));
        int count = 0;
        for (List<CurriculumItem> items : byGroup.values()) {
            LocalDate creditDay = LocalDate.of(year, 12, 21);
            LocalDate examDay = LocalDate.of(year + 1, 1, 12);
            int i = 0;
            int credits = 0;
            for (CurriculumItem c : items.stream().sorted(Comparator.comparing(x -> x.getSubject().getName())).toList()) {
                Exam exam = new Exam();
                exam.setPlan(c);
                if (c.getControlForm() == ControlForm.EXAM) {
                    exam.setDate(examDay);
                    exam.setStartTime(LocalTime.of(9, 0));
                    examDay = workday(examDay.plusDays(3 + (i % 2)));
                } else {
                    // Два зачёта в день: в 12:40 и в 14:10
                    exam.setDate(creditDay);
                    exam.setStartTime(credits % 2 == 0 ? LocalTime.of(12, 40) : LocalTime.of(14, 10));
                    if (credits % 2 == 1) {
                        creditDay = workday(creditDay.plusDays(1));
                    }
                    credits++;
                }
                exam.setRoom(rooms[(int) ((c.getGroup().getId() + i) % rooms.length)]);
                examRepository.save(exam);
                i++;
                count++;
            }
        }
        return count;
    }

    private static LocalDate workday(LocalDate d) {
        while (d.getDayOfWeek().getValue() > 5) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** История прохождения: каждый студент проходит тесты по дисциплинам своей группы с разной подготовкой. */
    private int simulateAttempts(SeedData data, Map<String, StudyGroup> groups, Map<String, List<User>> studentsByGroup,
                                 Map<String, List<Test>> testsBySubject) {
        Random random = new Random(42);
        int count = 0;
        for (PlanSeed p : data.curriculum()) {
            List<Test> tests = testsBySubject.getOrDefault(p.subject(), List.of());
            for (User student : studentsByGroup.getOrDefault(p.group(), List.of())) {
                double skill = 0.35 + (Math.abs(student.getUsername().hashCode()) % 60) / 100.0;
                for (Test test : tests) {
                    if (random.nextDouble() < 0.12) {
                        continue; // не пришёл
                    }
                    AppUserDetails me = new AppUserDetails(student, false);
                    TestAttempt attempt = attemptService.start(test.getId(), me);
                    Map<Long, Set<Long>> answers = new HashMap<>();
                    for (Question question : test.getQuestions()) {
                        Set<Long> selected = new HashSet<>();
                        if (random.nextDouble() < skill) {
                            question.getOptions().stream().filter(AnswerOption::isCorrect).forEach(o -> selected.add(o.getId()));
                        } else {
                            List<AnswerOption> options = question.getOptions();
                            selected.add(options.get(random.nextInt(options.size())).getId());
                        }
                        answers.put(question.getId(), selected);
                    }
                    attemptService.submit(attempt.getId(), answers, me);
                    int minutes = 3 + random.nextInt(Math.max(2, test.getTimeLimitMinutes() - 3));
                    LocalDateTime start = LocalDateTime.now().minusDays(1 + random.nextInt(40))
                            .withHour(8 + random.nextInt(9)).withMinute(random.nextInt(60)).withSecond(0).withNano(0);
                    attempt.setStartedAt(start);
                    attempt.setFinishedAt(start.plusMinutes(minutes));
                    count++;
                }
            }
        }
        return count;
    }

    private User newUser(String fullName, Role role, String passwordHash, Set<String> logins) {
        String base = NameGenerator.login(fullName);
        String login = base;
        for (int n = 2; logins.contains(login) || userRepository.existsByUsernameIgnoreCase(login); n++) {
            login = base + n;
        }
        logins.add(login);
        User u = new User();
        u.setUsername(login);
        u.setFullName(fullName);
        u.setEmail(login + "@rtsu-demo.example");
        u.setRole(role);
        u.setPasswordHash(passwordHash);
        return u;
    }

    private static QuestionForm question(QuestionSeed q) {
        QuestionForm form = new QuestionForm();
        form.setText(q.text());
        form.setPoints(q.points());
        long correct = q.options().stream().filter(o -> o.endsWith("+")).count();
        form.setType(correct > 1 ? QuestionType.MULTIPLE : QuestionType.SINGLE);
        for (String option : q.options()) {
            boolean isCorrect = option.endsWith("+");
            form.getOptions().add(new OptionForm(isCorrect ? option.substring(0, option.length() - 1) : option, isCorrect));
        }
        return form;
    }

    private static <T> T required(Map<String, T> map, String key, String what) {
        T value = map.get(key);
        if (value == null) {
            throw new IllegalStateException("В файле начальных данных не найдена " + what + ": «" + key + "»");
        }
        return value;
    }
}
