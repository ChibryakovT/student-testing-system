package ru.edu.testing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.*;
import ru.edu.testing.dto.QuestionForm;
import ru.edu.testing.dto.OptionForm;
import ru.edu.testing.dto.RegistrationForm;
import ru.edu.testing.dto.SubjectForm;
import ru.edu.testing.dto.TestForm;
import ru.edu.testing.repository.UserRepository;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.*;

import java.security.SecureRandom;
import java.util.*;

/**
 * Создаёт администратора при первом запуске и (опционально) демонстрационные данные.
 * Пароли берутся из переменных окружения; если они не заданы — генерируются случайно
 * и однократно выводятся в лог. В исходном коде пароли не хранятся.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final SubjectService subjectService;
    private final TestService testService;
    private final QuestionService questionService;
    private final AttemptService attemptService;

    @Value("${app.admin.username:admin}")
    private String adminUsername;
    @Value("${app.admin.password:}")
    private String adminPassword;
    @Value("${app.demo.enabled:false}")
    private boolean demoEnabled;
    @Value("${app.demo.password:}")
    private String demoPassword;

    public DataInitializer(UserRepository userRepository, UserService userService, SubjectService subjectService,
                           TestService testService, QuestionService questionService, AttemptService attemptService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.subjectService = subjectService;
        this.testService = testService;
        this.questionService = questionService;
        this.attemptService = attemptService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        String adminPass = passwordOrRandom(adminPassword, "ADMIN_PASSWORD");
        userService.create(form(adminUsername, adminPass, "Администратор системы", "admin@example.com", null), Role.ADMIN);
        log.info("Создан администратор «{}»", adminUsername);
        if (demoEnabled) {
            createDemoData(passwordOrRandom(demoPassword, "DEMO_PASSWORD"));
        }
    }

    private void createDemoData(String password) {
        User teacher = userService.create(form("teacher", password, "Иванова Мария Петровна",
                "teacher@example.com", null), Role.TEACHER);
        List<User> students = List.of(
                userService.create(form("student", password, "Чибряков Тимур", "student@example.com", "ПИ-21"), Role.STUDENT),
                userService.create(form("petrov", password, "Петров Алексей Игоревич", "petrov@example.com", "ПИ-21"), Role.STUDENT),
                userService.create(form("sidorova", password, "Сидорова Анна Викторовна", "sidorova@example.com", "ПИ-21"), Role.STUDENT),
                userService.create(form("kuznetsov", password, "Кузнецов Дмитрий Олегович", "kuznetsov@example.com", "ПИ-22"), Role.STUDENT),
                userService.create(form("smirnova", password, "Смирнова Елена Сергеевна", "smirnova@example.com", "ПИ-22"), Role.STUDENT),
                userService.create(form("volkov", password, "Волков Никита Андреевич", "volkov@example.com", "ПИ-22"), Role.STUDENT));
        AppUserDetails author = new AppUserDetails(teacher, false);

        Subject db = subject("Базы данных", "Реляционная модель, SQL, проектирование БД");
        Subject java = subject("Программирование на Java", "Синтаксис, ООП, коллекции, исключения");
        Subject net = subject("Компьютерные сети", "Модель OSI, стек TCP/IP, адресация");
        Subject sec = subject("Информационная безопасность", "Угрозы, криптография, защита веб-приложений");

        List<Test> published = new ArrayList<>();
        published.add(test(author, db, "SQL: основы запросов", "Проверка знания базовых конструкций SQL", 15, 60,
                q("Какой оператор используется для выборки данных?", 1, "SELECT+", "INSERT", "UPDATE", "DELETE"),
                q("Какое ключевое слово убирает дубликаты строк в результате?", 1, "DISTINCT+", "UNIQUE", "GROUP", "LIMIT"),
                q("Какие из перечисленных функций являются агрегатными?", 2, "COUNT+", "SUM+", "AVG+", "UPPER"),
                q("Какое условие фильтрует результаты после группировки?", 1, "HAVING+", "WHERE", "ORDER BY", "ON"),
                q("Какой JOIN вернёт все строки левой таблицы?", 1, "LEFT JOIN+", "INNER JOIN", "RIGHT JOIN", "CROSS JOIN")));
        published.add(test(author, db, "Нормализация баз данных", "Нормальные формы и ключи", 20, 70,
                q("Первичный ключ таблицы должен быть…", 1, "уникальным и не NULL+", "только числовым", "внешним ключом", "необязательным"),
                q("Что устраняет вторая нормальная форма?", 1, "частичные зависимости от составного ключа+", "транзитивные зависимости", "повторяющиеся группы", "внешние ключи"),
                q("Внешний ключ обеспечивает…", 1, "ссылочную целостность+", "шифрование данных", "сортировку", "индексацию"),
                q("Какие связи между таблицами существуют?", 2, "один к одному+", "один ко многим+", "многие ко многим+", "все ко всем")));
        published.add(test(author, java, "Основы Java", "Типы данных, операторы, строки", 15, 60,
                q("Какой тип используется для целых 64-битных чисел?", 1, "long+", "int", "short", "double"),
                q("Как сравнить содержимое двух строк?", 1, "a.equals(b)+", "a == b", "a = b", "a.compare(b)"),
                q("Какие из перечисленных типов являются примитивными?", 2, "int+", "boolean+", "String", "Integer"),
                q("Что выведет выражение 7 / 2 для int?", 1, "3+", "3.5", "4", "ошибка компиляции")));
        published.add(test(author, java, "ООП в Java", "Классы, наследование, интерфейсы", 20, 60,
                q("Какое ключевое слово используется для наследования класса?", 1, "extends+", "implements", "inherits", "super"),
                q("Сколько классов может напрямую наследовать класс в Java?", 1, "один+", "два", "сколько угодно", "ни одного"),
                q("Какие принципы относятся к ООП?", 2, "инкапсуляция+", "наследование+", "полиморфизм+", "компиляция"),
                q("Модификатор, ограничивающий доступ пределами класса:", 1, "private+", "public", "protected", "static")));
        published.add(test(author, net, "Модель OSI", "Уровни модели и протоколы", 10, 50,
                q("Сколько уровней в модели OSI?", 1, "7+", "4", "5", "9"),
                q("На каком уровне работает протокол IP?", 1, "сетевом+", "канальном", "транспортном", "прикладном"),
                q("Какие протоколы относятся к транспортному уровню?", 2, "TCP+", "UDP+", "HTTP", "ARP")));
        Test draft = test(author, sec, "Основы криптографии", "Черновик: симметричное и асимметричное шифрование", 25, 70,
                q("Какой алгоритм является симметричным?", 1, "AES+", "RSA", "ECDSA", "Диффи — Хеллман"));
        draft.setPublished(false);

        simulateAttempts(published, students);
        log.info("Созданы демонстрационные данные: {} студентов, {} тестов", students.size(), published.size() + 1);
    }

    /** Имитация прохождения тестов студентами для наполнения аналитического отчёта. */
    private void simulateAttempts(List<Test> tests, List<User> students) {
        Random random = new Random(42);
        int dayOffset = 30;
        for (User student : students) {
            AppUserDetails me = new AppUserDetails(student, false);
            double skill = 0.45 + random.nextDouble() * 0.5;
            for (Test test : tests) {
                if (random.nextDouble() < 0.2) {
                    continue;
                }
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
                attempt.setStartedAt(java.time.LocalDateTime.now().minusDays(dayOffset).minusMinutes(minutes)
                        .withSecond(0).withNano(0));
                attempt.setFinishedAt(attempt.getStartedAt().plusMinutes(minutes));
                dayOffset = Math.max(1, dayOffset - 1);
            }
        }
    }

    private Subject subject(String name, String description) {
        SubjectForm form = new SubjectForm();
        form.setName(name);
        form.setDescription(description);
        return subjectService.create(form);
    }

    private Test test(AppUserDetails author, Subject subject, String title, String description, int minutes,
                      int passing, QuestionForm... questions) {
        TestForm form = new TestForm();
        form.setTitle(title);
        form.setDescription(description);
        form.setSubjectId(subject.getId());
        form.setTimeLimitMinutes(minutes);
        form.setPassingScore(passing);
        Test test = testService.create(form, author);
        for (QuestionForm question : questions) {
            questionService.add(test.getId(), question, author);
        }
        test.setPublished(true);
        return test;
    }

    /** Вопрос из строк; правильные варианты помечены «+» в конце. */
    private static QuestionForm q(String text, int points, String... options) {
        QuestionForm form = new QuestionForm();
        form.setText(text);
        form.setPoints(points);
        long correct = Arrays.stream(options).filter(o -> o.endsWith("+")).count();
        form.setType(correct > 1 ? QuestionType.MULTIPLE : QuestionType.SINGLE);
        for (String option : options) {
            boolean isCorrect = option.endsWith("+");
            form.getOptions().add(new OptionForm(isCorrect ? option.substring(0, option.length() - 1) : option, isCorrect));
        }
        return form;
    }

    private static RegistrationForm form(String username, String password, String fullName, String email, String group) {
        RegistrationForm form = new RegistrationForm();
        form.setUsername(username);
        form.setPassword(password);
        form.setFullName(fullName);
        form.setEmail(email);
        form.setGroupName(group);
        return form;
    }

    private static String passwordOrRandom(String configured, String envName) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        log.warn("Переменная {} не задана. Сгенерирован пароль: {} (сохраните его, он больше не будет показан)",
                envName, sb);
        return sb.toString();
    }
}
