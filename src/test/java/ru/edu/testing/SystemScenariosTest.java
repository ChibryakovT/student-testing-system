package ru.edu.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Сценарии тестирования системы (соответствуют таблице тестовых случаев в отчёте).
 * Запросы идут через полный стек: Spring Security → контроллер → сервис → БД (H2 в режиме PostgreSQL).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SystemScenariosTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASS = "Admin-test-1";
    private static final String PASS = "Passw0rd-123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    private String teacher;
    private String student;
    /** Учебная группа студента; дисциплины теста добавляются в её учебный план. */
    private long groupId;
    private final Map<String, Long> userIds = new java.util.HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        groupId = createGroup().get("id").asLong();
        teacher = createUser("TEACHER");
        student = createUser("STUDENT", groupId);
    }

    // ---------- Авторизация и регистрация ----------

    @Test
    @DisplayName("1. Авторизация: правильный логин и пароль — вход выполнен")
    void loginSuccess() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", student).param("password", PASS))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("2. Авторизация: неверный пароль — сообщение об ошибке")
    void loginWrongPassword() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", student).param("password", "wrong-pass1"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("3. Защита от подбора: после 5 ошибок вход блокируется даже с верным паролем")
    void bruteForceLock() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/login").with(csrf()).param("username", student).param("password", "bad" + i))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(post("/login").with(csrf()).param("username", student).param("password", PASS))
                .andExpect(redirectedUrl("/login?locked"));
        // Несуществующий логин блокируется так же: по ответу нельзя определить, есть ли пользователь
        String ghost = "ghost" + SEQ.incrementAndGet();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/login").with(csrf()).param("username", ghost).param("password", "bad" + i));
        }
        mvc.perform(post("/login").with(csrf()).param("username", ghost).param("password", "any"))
                .andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    @DisplayName("4. Регистрация: корректные данные — создан студент, роль из запроса игнорируется")
    void registerStudent() throws Exception {
        String login = "newuser" + SEQ.incrementAndGet();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", login, "password", PASS,
                                "fullName", "Новый Студент", "email", login + "@example.com",
                                "groupId", groupId, "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("5. Регистрация: занятый логин — ошибка 409")
    void registerDuplicate() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", student, "password", PASS,
                                "fullName", "Дубликат", "email", "dup" + SEQ.incrementAndGet() + "@example.com"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("уже занят")));
    }

    @Test
    @DisplayName("6. Регистрация: слабый пароль и пустое ФИО — ошибка валидации 400")
    void registerValidation() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "x" + SEQ.incrementAndGet(),
                                "password", "123", "fullName", "", "email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("7. Запрос к API без авторизации — 401")
    void unauthorized() throws Exception {
        mvc.perform(get("/api/tests")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. CSRF: POST-форма без токена отклоняется — 403")
    void csrfProtection() throws Exception {
        mvc.perform(post("/login").param("username", student).param("password", PASS))
                .andExpect(status().isForbidden());
    }

    // ---------- Добавление, редактирование, удаление ----------

    @Test
    @DisplayName("9. Добавление дисциплины преподавателем — запись добавлена")
    void addSubject() throws Exception {
        String name = "Дисциплина " + SEQ.incrementAndGet();
        api(post("/api/subjects"), teacher, Map.of("name", name, "description", "Описание"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    @DisplayName("10. Студент пытается создать тест — доступ запрещён 403")
    void studentCannotCreateTest() throws Exception {
        long subjectId = createSubject();
        api(post("/api/tests"), student, testBody(subjectId, "Тест студента", false))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11. Вопрос без правильного ответа — ошибка 400")
    void questionWithoutCorrectAnswer() throws Exception {
        long testId = createTest(createSubject(), "Тест без ответа");
        api(post("/api/tests/" + testId + "/questions"), teacher, Map.of("text", "Вопрос?", "type", "SINGLE",
                "points", 1, "options", List.of(Map.of("text", "А", "correct", false),
                        Map.of("text", "Б", "correct", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("правильный")));
    }

    @Test
    @DisplayName("12. Редактирование чужого теста другим преподавателем — 403")
    void foreignTeacherCannotEdit() throws Exception {
        long subjectId = createSubject();
        long testId = createTest(subjectId, "Тест автора");
        String otherTeacher = createUser("TEACHER");
        api(put("/api/tests/" + testId), otherTeacher, testBody(subjectId, "Взлом", false))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("13. Удаление дисциплины, по которой есть тесты — 409, данные не потеряны")
    void deleteSubjectWithTests() throws Exception {
        long subjectId = createSubject();
        createTest(subjectId, "Связанный тест");
        api(delete("/api/subjects/" + subjectId), teacher, null).andExpect(status().isConflict());
        api(get("/api/subjects/" + subjectId), teacher, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("14. Удаление теста автором — 204, затем 404")
    void deleteTest() throws Exception {
        long testId = createTest(createSubject(), "Удаляемый тест");
        api(delete("/api/tests/" + testId), teacher, null).andExpect(status().isNoContent());
        api(get("/api/tests/" + testId), teacher, null).andExpect(status().isNotFound());
    }

    // ---------- Поиск и фильтрация ----------

    @Test
    @DisplayName("15. Поиск теста по названию и фильтр по дисциплине")
    void searchAndFilter() throws Exception {
        long subjectA = createSubject();
        long subjectB = createSubject();
        String unique = "Уникум" + SEQ.incrementAndGet();
        createTest(subjectA, unique + " основной");
        createTest(subjectB, unique + " другой");
        api(get("/api/tests").param("q", unique.toLowerCase()).param("subjectId", String.valueOf(subjectA)),
                teacher, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value(unique + " основной"));
    }

    @Test
    @DisplayName("16. Студент не видит черновик теста — 404")
    void studentCannotSeeDraft() throws Exception {
        long testId = createTest(createSubject(), "Черновик");
        api(get("/api/tests/" + testId), student, null).andExpect(status().isNotFound());
    }

    // ---------- Прохождение и результаты ----------

    @Test
    @DisplayName("17. Прохождение теста с правильными ответами — 100%, тест сдан")
    void passTest() throws Exception {
        long testId = createPublishedTest();
        JsonNode started = startAttempt(testId, student);
        started.path("questions").forEach(q -> q.path("options")
                .forEach(o -> org.junit.jupiter.api.Assertions.assertTrue(o.path("correct").isMissingNode()
                        || o.path("correct").isNull(), "Студенту не должны приходить правильные ответы")));
        api(post("/api/attempts/" + started.get("attemptId").asLong() + "/submit"), student,
                Map.of("answers", correctAnswers(testId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(100))
                .andExpect(jsonPath("$.passed").value(true));
    }

    @Test
    @DisplayName("18. Повторная отправка завершённой попытки — 409")
    void resubmitFinished() throws Exception {
        long testId = createPublishedTest();
        long attemptId = startAttempt(testId, student).get("attemptId").asLong();
        Map<String, Object> body = Map.of("answers", List.of());
        api(post("/api/attempts/" + attemptId + "/submit"), student, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(0));
        api(post("/api/attempts/" + attemptId + "/submit"), student, body).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("19. Студент не может открыть чужой результат — 404")
    void foreignResultHidden() throws Exception {
        long testId = createPublishedTest();
        long attemptId = startAttempt(testId, student).get("attemptId").asLong();
        api(post("/api/attempts/" + attemptId + "/submit"), student, Map.of("answers", List.of()))
                .andExpect(status().isOk());
        String otherStudent = createUser("STUDENT", groupId);
        api(get("/api/attempts/" + attemptId), otherStudent, null).andExpect(status().isNotFound());
        api(get("/api/attempts/" + attemptId), teacher, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("20. Аналитический отчёт: администратору доступен, преподавателю и студенту — 403")
    void analyticsReport() throws Exception {
        long testId = createPublishedTest();
        long attemptId = startAttempt(testId, student).get("attemptId").asLong();
        api(post("/api/attempts/" + attemptId + "/submit"), student, Map.of("answers", correctAnswers(testId)))
                .andExpect(status().isOk());
        api(get("/api/reports/tests"), ADMIN, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.testId == " + testId + ")].attempts").value(contains(1)))
                .andExpect(jsonPath("$[?(@.testId == " + testId + ")].averagePercent").value(contains(100.0)));
        api(get("/api/reports/tests"), teacher, null).andExpect(status().isForbidden());
        api(get("/api/reports/tests"), student, null).andExpect(status().isForbidden());
        mvc.perform(get("/reports").with(user(teacher).roles("TEACHER"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("21. Администратор: фильтр пользователей и результатов по группе")
    void adminGroupFilter() throws Exception {
        JsonNode group = createGroup();
        long otherGroup = group.get("id").asLong();
        String login = createUser("STUDENT", otherGroup);
        api(get("/api/users").param("groupId", String.valueOf(otherGroup)), ADMIN, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value(login));

        long testId = createPublishedTest();
        addPlan(otherGroup, subjectOf(testId), teacher, "EXAM");
        long attemptId = startAttempt(testId, login).get("attemptId").asLong();
        api(post("/api/attempts/" + attemptId + "/submit"), login, Map.of("answers", List.of()))
                .andExpect(status().isOk());
        api(get("/api/attempts").param("groupId", String.valueOf(otherGroup)), ADMIN, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].groupName").value(group.get("name").asText()));
    }

    @Test
    @DisplayName("22. Ведомость: CSV и Excel по группе, оценки по шкале, зачёт, отметка «н/я»; преподавателю — 403")
    void gradeSheetExport() throws Exception {
        JsonNode group = createGroup();
        long sheetGroup = group.get("id").asLong();
        String passed = createUser("STUDENT", sheetGroup);
        String absent = createUser("STUDENT", sheetGroup);
        long testId = createPublishedTest();
        long subjectId = subjectOf(testId);
        addPlan(sheetGroup, subjectId, teacher, "EXAM");
        long attemptId = startAttempt(testId, passed).get("attemptId").asLong();
        api(post("/api/attempts/" + attemptId + "/submit"), passed, Map.of("answers", correctAnswers(testId)))
                .andExpect(status().isOk());

        String csv = sheetCsv(sheetGroup, subjectId, testId, "NUM", "NAME", "GRADE", "GRADE_WORD");
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("Группа: " + group.get("name").asText()), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("Пользователь " + passed + ";5;отлично"), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("Пользователь " + absent + ";н/я;не явился"), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("5 (отл.) = 1") && csv.contains("не явились = 1"), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("Форма контроля: экзамен"), "форма контроля из плана: " + csv);

        // Зачёт по учебному плану группы: вместо оценки — «зачтено / не зачтено»
        addPlan(sheetGroup, subjectId, teacher, "CREDIT");
        String credit = sheetCsv(sheetGroup, subjectId, testId, "NAME", "GRADE", "GRADE_WORD");
        org.junit.jupiter.api.Assertions.assertTrue(credit.contains("Пользователь " + passed + ";зач.;зачтено"), credit);
        org.junit.jupiter.api.Assertions.assertTrue(credit.contains("зачтено = 1, не зачтено = 0, не явились = 1"), credit);

        byte[] xlsx = mvc.perform(get("/reports/sheet/export.xlsx").with(user(ADMIN).roles("ADMIN"))
                        .param("groupId", String.valueOf(sheetGroup)).param("subjectId", String.valueOf(subjectId))
                        .param("testId", String.valueOf(testId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            org.junit.jupiter.api.Assertions.assertEquals("Ведомость", wb.getSheetAt(0).getSheetName());
        }
        mvc.perform(get("/reports/sheet").with(user(teacher).roles("TEACHER"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("23. Учебный план: студент другой группы не видит тест по чужой дисциплине — 404")
    void studentOfOtherGroupCannotSeeTest() throws Exception {
        long testId = createPublishedTest();
        String outsider = createUser("STUDENT", createGroup().get("id").asLong());
        api(get("/api/tests/" + testId), outsider, null).andExpect(status().isNotFound());
        api(post("/api/tests/" + testId + "/attempts"), outsider, null).andExpect(status().isNotFound());
        api(get("/api/tests"), outsider, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + testId + ")]").isEmpty());
        api(get("/api/tests/" + testId), student, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("24. Учебный план: преподаватель не создаёт тесты по чужой дисциплине и не видит чужие результаты")
    void teacherLimitedToOwnSubjects() throws Exception {
        long subjectId = createSubject();
        String other = createUser("TEACHER");
        api(post("/api/tests"), other, testBody(subjectId, "Чужой предмет", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("не ведёте")));
        long draft = createTest(subjectId, "Черновик коллеги");
        api(get("/api/tests/" + draft), other, null).andExpect(status().isNotFound());

        long testId = createPublishedTest();
        long attemptId = startAttempt(testId, student).get("attemptId").asLong();
        api(post("/api/attempts/" + attemptId + "/submit"), student, Map.of("answers", List.of()))
                .andExpect(status().isOk());
        api(get("/api/attempts/" + attemptId), other, null).andExpect(status().isNotFound());
        api(get("/api/attempts"), other, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        api(get("/api/attempts/" + attemptId), teacher, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("25. Ведомость: шапка заполняется из учебного плана; дисциплина не из плана группы — ошибка")
    void sheetHeaderFromCurriculum() throws Exception {
        JsonNode group = createGroup();
        long sheetGroup = group.get("id").asLong();
        long testId = createPublishedTest();
        long subjectId = subjectOf(testId);
        mvc.perform(get("/reports/sheet/export.csv").with(user(ADMIN).roles("ADMIN"))
                        .param("groupId", String.valueOf(sheetGroup)).param("subjectId", String.valueOf(subjectId))
                        .param("testId", String.valueOf(testId)))
                .andExpect(status().isBadRequest());

        addPlan(sheetGroup, subjectId, teacher, "GRADED_CREDIT");
        String csv = sheetCsv(sheetGroup, subjectId, testId, "NAME", "GRADE");
        String subjectName = json.readTree(api(get("/api/subjects/" + subjectId), teacher, null)
                .andReturn().getResponse().getContentAsString()).get("name").asText();
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("Дисциплина: " + subjectName), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("Преподаватель: Пользователь " + teacher), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("Форма контроля: дифференцированный зачёт"), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("Группа: " + group.get("name").asText() + ", 2 курс"), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("Специальность: 09.03.03 Прикладная информатика"), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("3 семестр"), csv);
    }

    @Test
    @DisplayName("26. Расписание: смотреть могут все, изменять — только администратор")
    void scheduleAccess() throws Exception {
        long subjectId = createSubject();
        // Студент и преподаватель не могут добавить занятие
        mvc.perform(post("/schedule/lessons").with(user(student).roles("STUDENT")).with(csrf())
                        .param("groupId", String.valueOf(groupId)).param("day", "1").param("pair", "1")
                        .param("subjectId", String.valueOf(subjectId)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/schedule/lessons").with(user(teacher).roles("TEACHER")).with(csrf())
                        .param("groupId", String.valueOf(groupId)).param("day", "1").param("pair", "1")
                        .param("subjectId", String.valueOf(subjectId)))
                .andExpect(status().isForbidden());
        // Администратор добавляет пару по числителю
        mvc.perform(post("/schedule/lessons").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("groupId", String.valueOf(groupId)).param("day", "2").param("pair", "3")
                        .param("week", "NUMERATOR").param("subjectId", String.valueOf(subjectId))
                        .param("teacherId", String.valueOf(userIds.get(teacher))).param("room", "214"))
                .andExpect(status().is3xxRedirection());
        api(get("/api/schedule/groups/" + groupId), student, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessons[0].day").value(2))
                .andExpect(jsonPath("$.lessons[0].week").value("NUMERATOR"))
                .andExpect(jsonPath("$.lessons[0].room").value("214"));
        // Экзамен назначается по строке учебного плана: форма контроля и преподаватель — из плана
        String plan = api(get("/api/groups/" + groupId + "/curriculum"), ADMIN, null)
                .andReturn().getResponse().getContentAsString();
        long planId = json.readTree(plan).get(0).get("id").asLong();
        mvc.perform(post("/schedule/exams").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("groupId", String.valueOf(groupId)).param("planId", String.valueOf(planId))
                        .param("date", "2027-01-15").param("time", "09:00").param("room", "221"))
                .andExpect(status().is3xxRedirection());
        api(get("/api/schedule/groups/" + groupId), student, null)
                .andExpect(jsonPath("$.exams[0].date").value("2027-01-15"))
                .andExpect(jsonPath("$.exams[0].controlForm").value("экзамен"))
                .andExpect(jsonPath("$.exams[0].teacher").value("Пользователь " + teacher));
        // Студент входит через форму и открывает страницу расписания своей группы
        var session = (org.springframework.mock.web.MockHttpSession) mvc.perform(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders
                                .formLogin().user(student).password(PASS))
                .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/schedule").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("числитель")));
    }

    @Test
    @DisplayName("27. Лимит попыток: после исчерпания новая попытка запрещена — 409")
    void attemptLimit() throws Exception {
        long testId = createPublishedTest();
        Map<String, Object> body = new java.util.HashMap<>(testBody(subjectOf(testId), "Тест с лимитом", true));
        body.put("maxAttempts", 1);
        api(put("/api/tests/" + testId), teacher, body).andExpect(status().isOk());
        long attemptId = startAttempt(testId, student).get("attemptId").asLong();
        api(post("/api/attempts/" + attemptId + "/submit"), student, Map.of("answers", List.of())).andExpect(status().isOk());
        api(post("/api/tests/" + testId + "/attempts"), student, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("лимит попыток")));
    }

    @Test
    @DisplayName("28. Перемешивание: порядок вопросов у попытки постоянный, набор вопросов полный")
    void shuffleIsStablePerAttempt() throws Exception {
        long testId = createPublishedTest();
        for (int i = 0; i < 4; i++) {
            api(post("/api/tests/" + testId + "/questions"), teacher, Map.of("text", "Вопрос " + i, "type", "SINGLE",
                    "points", 1, "options", List.of(Map.of("text", "да", "correct", true), Map.of("text", "нет", "correct", false))))
                    .andExpect(status().isCreated());
        }
        Map<String, Object> body = new java.util.HashMap<>(testBody(subjectOf(testId), "Перемешанный тест", true));
        body.put("shuffle", true);
        api(put("/api/tests/" + testId), teacher, body).andExpect(status().isOk());
        JsonNode first = startAttempt(testId, student);
        JsonNode again = startAttempt(testId, student);   // незавершённая попытка продолжается
        org.junit.jupiter.api.Assertions.assertEquals(first.get("attemptId"), again.get("attemptId"));
        org.junit.jupiter.api.Assertions.assertEquals(first.get("questions"), again.get("questions"), "порядок не должен меняться");
        org.junit.jupiter.api.Assertions.assertEquals(6, first.get("questions").size());
    }

    @Test
    @DisplayName("29. Импорт вопросов из Excel: корректный файл добавляет вопросы, ошибка в строке — ничего не добавляет")
    void importQuestionsFromExcel() throws Exception {
        long testId = createTest(createSubject(), "Импорт");
        var session = login(teacher, PASS);
        mvc.perform(multipart("/tests/" + testId + "/questions/import").file(xlsx(
                        new String[]{"2 + 2 = ?", "1", "4+", "5"}, new String[]{"Чётные числа", "2", "2+", "3", "8+"}))
                        .session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "Импортировано вопросов: 2"));
        mvc.perform(multipart("/tests/" + testId + "/questions/import").file(xlsx(
                        new String[]{"Хороший вопрос", "1", "да+", "нет"}, new String[]{"Без правильного", "1", "а", "б"}))
                        .session(session).with(csrf()))
                .andExpect(flash().attribute("errorMessage", containsString("Строка 3")));
        api(get("/api/tests/" + testId + "/questions"), teacher, null)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].type").value("MULTIPLE"));
    }

    @Test
    @DisplayName("30. Журнал действий: создание теста записывается, журнал виден только администратору")
    void auditLog() throws Exception {
        String title = "Журналируемый тест " + SEQ.incrementAndGet();
        id(api(post("/api/tests"), teacher, testBody(createSubject(), title, false)));
        mvc.perform(get("/audit").session(login(ADMIN, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Тест создан")))
                .andExpect(content().string(containsString(title)));
        mvc.perform(get("/audit").session(login(teacher, PASS))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("31. Профиль: смена пароля — старый пароль перестаёт работать, новый работает")
    void changePassword() throws Exception {
        String login = createUser("STUDENT", groupId);
        var session = login(login, PASS);
        mvc.perform(post("/profile/password").session(session).with(csrf())
                        .param("currentPassword", "wrong-pass1").param("newPassword", "NewPass-2026").param("confirmPassword", "NewPass-2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Текущий пароль указан неверно")));
        mvc.perform(post("/profile/password").session(session).with(csrf())
                        .param("currentPassword", PASS).param("newPassword", "NewPass-2026").param("confirmPassword", "NewPass-2026"))
                .andExpect(redirectedUrl("/profile"));
        mvc.perform(post("/login").with(csrf()).param("username", login).param("password", PASS))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).param("username", login).param("password", "NewPass-2026"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("32. Аналитика: сравнение групп и рейтинг студентов в отчёте администратора")
    void groupAnalytics() throws Exception {
        long testId = createPublishedTest();
        long attemptId = startAttempt(testId, student).get("attemptId").asLong();
        api(post("/api/attempts/" + attemptId + "/submit"), student, Map.of("answers", correctAnswers(testId)))
                .andExpect(status().isOk());
        mvc.perform(get("/reports").param("subjectId", String.valueOf(subjectOf(testId))).session(login(ADMIN, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Сравнение групп")))
                .andExpect(content().string(containsString("Рейтинг студентов")))
                .andExpect(content().string(containsString("Пользователь " + student)));
    }

    @Test
    @DisplayName("33. Дисциплины: администратор меняет преподавателя группы, преподаватель — нет; расписание преподавателя только своё")
    void assignTeacherAndOwnSchedule() throws Exception {
        long subjectId = createSubject();   // в плане группы groupId, ведёт teacher
        String other = createUser("TEACHER");
        String plan = api(get("/api/groups/" + groupId + "/curriculum"), ADMIN, null).andReturn().getResponse().getContentAsString();
        long planId = -1;
        for (JsonNode c : json.readTree(plan)) {
            if (c.get("subjectId").asLong() == subjectId) {
                planId = c.get("id").asLong();
            }
        }
        // Преподаватель не может переназначить дисциплину
        mvc.perform(post("/subjects/" + subjectId + "/teachers").session(login(teacher, PASS)).with(csrf())
                        .param("planId", String.valueOf(planId)).param("teacherId", String.valueOf(userIds.get(other))))
                .andExpect(status().isForbidden());
        // Администратор назначает другого преподавателя — меняется учебный план
        mvc.perform(post("/subjects/" + subjectId + "/teachers").session(login(ADMIN, ADMIN_PASS)).with(csrf())
                        .param("planId", String.valueOf(planId)).param("teacherId", String.valueOf(userIds.get(other))))
                .andExpect(redirectedUrl("/subjects/" + subjectId + "/edit#teachers"));
        api(get("/api/groups/" + groupId + "/curriculum"), ADMIN, null)
                .andExpect(jsonPath("$[?(@.subjectId == " + subjectId + ")].teacherId").value(contains(userIds.get(other).intValue())));
        // Расписание преподавателя: параметр чужой группы игнорируется, показывается «Моё расписание»
        mvc.perform(get("/schedule").param("groupId", String.valueOf(groupId)).session(login(other, PASS)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Моё расписание")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"groupId\""))));
    }

    @Test
    @DisplayName("34. Расписание: студент видит только свою группу, преподаватель — только своё (страница и API)")
    void scheduleOnlyOwn() throws Exception {
        JsonNode other = createGroup();
        long otherGroup = other.get("id").asLong();
        String ownGroupName = json.readTree(api(get("/api/auth/me"), student, null).andReturn().getResponse()
                .getContentAsString()).get("groupName").asText();
        // Страница: подставленная в адрес чужая группа игнорируется
        mvc.perform(get("/schedule").param("groupId", String.valueOf(otherGroup)).session(login(student, PASS)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Группа " + ownGroupName)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Группа " + other.get("name").asText()))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"groupId\""))));
        // API: своя группа — да, чужая — 404
        api(get("/api/schedule/groups/" + groupId), student, null).andExpect(status().isOk());
        api(get("/api/schedule/groups/" + otherGroup), student, null).andExpect(status().isNotFound());
        // Преподаватель: своё расписание — да, чужое — 404; администратор — любое
        String colleague = createUser("TEACHER");
        api(get("/api/schedule/teachers/" + userIds.get(teacher)), teacher, null).andExpect(status().isOk());
        api(get("/api/schedule/teachers/" + userIds.get(colleague)), teacher, null).andExpect(status().isNotFound());
        api(get("/api/schedule/groups/" + otherGroup), teacher, null).andExpect(status().isNotFound());
        api(get("/api/schedule/groups/" + otherGroup), ADMIN, null).andExpect(status().isOk());
    }

    // ---------- Вспомогательные методы ----------

    private ResultActions api(MockHttpServletRequestBuilder request, String user, Object body) throws Exception {
        String password = ADMIN.equals(user) ? ADMIN_PASS : PASS;
        request.with(httpBasic(user, password));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private String createUser(String role) throws Exception {
        return createUser(role, null);
    }

    private String createUser(String role, Long group) throws Exception {
        String login = role.toLowerCase() + SEQ.incrementAndGet();
        Map<String, Object> body = new java.util.HashMap<>(Map.of("username", login, "password", PASS,
                "fullName", "Пользователь " + login, "email", login + "@example.com", "role", role));
        if (group != null) {
            body.put("groupId", group);
        }
        String response = api(post("/api/users"), ADMIN, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        userIds.put(login, json.readTree(response).get("id").asLong());
        return login;
    }

    private JsonNode createGroup() throws Exception {
        String response = api(post("/api/groups"), ADMIN, Map.of("name", "Г-" + SEQ.incrementAndGet(), "course", 2,
                "specialty", "09.03.03 Прикладная информатика"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    /** Добавить (или обновить) дисциплину в учебном плане группы: преподаватель, 3 семестр, форма контроля. */
    private void addPlan(long group, long subjectId, String teacherLogin, String form) throws Exception {
        api(post("/api/groups/" + group + "/curriculum"), ADMIN, Map.of("subjectId", subjectId,
                "teacherId", userIds.get(teacherLogin), "semester", 3, "controlForm", form))
                .andExpect(status().isCreated());
    }

    /** Новая дисциплина, которую по учебному плану группы ведёт преподаватель teacher. */
    private long createSubject() throws Exception {
        long subjectId = id(api(post("/api/subjects"), teacher, Map.of("name", "Предмет " + SEQ.incrementAndGet())));
        addPlan(groupId, subjectId, teacher, "EXAM");
        return subjectId;
    }

    private long subjectOf(long testId) throws Exception {
        return json.readTree(api(get("/api/tests/" + testId), teacher, null).andReturn().getResponse()
                .getContentAsString()).get("subjectId").asLong();
    }

    /** Вход через форму — сессия с настоящим пользователем системы. */
    private org.springframework.mock.web.MockHttpSession login(String username, String password) throws Exception {
        return (org.springframework.mock.web.MockHttpSession) mvc.perform(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders
                                .formLogin().user(username).password(password))
                .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
    }

    /** Файл Excel с вопросами: первая строка — заголовок. */
    private org.springframework.mock.web.MockMultipartFile xlsx(String[]... rows) throws Exception {
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var out = new java.io.ByteArrayOutputStream()) {
            var sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Вопрос");
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(out);
            return new org.springframework.mock.web.MockMultipartFile("file", "questions.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private String sheetCsv(long group, long subjectId, long testId, String... columns) throws Exception {
        return mvc.perform(get("/reports/sheet/export.csv").with(user(ADMIN).roles("ADMIN"))
                        .param("groupId", String.valueOf(group)).param("subjectId", String.valueOf(subjectId))
                        .param("testId", String.valueOf(testId)).param("columns", columns))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private Map<String, Object> testBody(long subjectId, String title, boolean published) {
        return Map.of("title", title, "subjectId", subjectId, "timeLimitMinutes", 10, "passingScore", 60,
                "published", published);
    }

    private long createTest(long subjectId, String title) throws Exception {
        return id(api(post("/api/tests"), teacher, testBody(subjectId, title, false)));
    }

    private long createPublishedTest() throws Exception {
        long subjectId = createSubject();
        long testId = createTest(subjectId, "Опубликованный тест " + SEQ.incrementAndGet());
        api(post("/api/tests/" + testId + "/questions"), teacher, Map.of("text", "2 + 2 = ?", "type", "SINGLE",
                "points", 1, "options", List.of(Map.of("text", "4", "correct", true),
                        Map.of("text", "5", "correct", false))))
                .andExpect(status().isCreated());
        api(post("/api/tests/" + testId + "/questions"), teacher, Map.of("text", "Чётные числа", "type", "MULTIPLE",
                "points", 2, "options", List.of(Map.of("text", "2", "correct", true),
                        Map.of("text", "3", "correct", false), Map.of("text", "8", "correct", true))))
                .andExpect(status().isCreated());
        api(put("/api/tests/" + testId), teacher, testBody(subjectId, "Опубликованный тест", true))
                .andExpect(status().isOk());
        return testId;
    }

    private JsonNode startAttempt(long testId, String user) throws Exception {
        String body = api(post("/api/tests/" + testId + "/attempts"), user, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private List<Map<String, Object>> correctAnswers(long testId) throws Exception {
        String body = api(get("/api/tests/" + testId + "/questions"), teacher, null)
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> answers = new ArrayList<>();
        for (JsonNode q : json.readTree(body)) {
            List<Long> ids = new ArrayList<>();
            q.path("options").forEach(o -> {
                if (o.path("correct").asBoolean()) {
                    ids.add(o.get("id").asLong());
                }
            });
            answers.add(Map.of("questionId", q.get("id").asLong(), "optionIds", ids));
        }
        return answers;
    }

    private long id(ResultActions actions) throws Exception {
        String body = actions.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}
