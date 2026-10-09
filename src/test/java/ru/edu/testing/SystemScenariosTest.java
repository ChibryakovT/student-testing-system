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

    @BeforeEach
    void setUp() throws Exception {
        teacher = createUser("TEACHER");
        student = createUser("STUDENT");
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
                                "groupName", "ПИ-21", "role", "ADMIN"))))
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
        String otherStudent = createUser("STUDENT");
        api(get("/api/attempts/" + attemptId), otherStudent, null).andExpect(status().isNotFound());
        api(get("/api/attempts/" + attemptId), teacher, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("20. Аналитический отчёт: преподавателю доступен, студенту — 403")
    void analyticsReport() throws Exception {
        long testId = createPublishedTest();
        long attemptId = startAttempt(testId, student).get("attemptId").asLong();
        api(post("/api/attempts/" + attemptId + "/submit"), student, Map.of("answers", correctAnswers(testId)))
                .andExpect(status().isOk());
        api(get("/api/reports/tests"), teacher, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.testId == " + testId + ")].attempts").value(contains(1)))
                .andExpect(jsonPath("$[?(@.testId == " + testId + ")].averagePercent").value(contains(100.0)));
        api(get("/api/reports/tests"), student, null).andExpect(status().isForbidden());
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
        String login = role.toLowerCase() + SEQ.incrementAndGet();
        api(post("/api/users"), ADMIN, Map.of("username", login, "password", PASS, "fullName", "Пользователь " + login,
                "email", login + "@example.com", "role", role))
                .andExpect(status().isCreated());
        return login;
    }

    private long createSubject() throws Exception {
        return id(api(post("/api/subjects"), teacher, Map.of("name", "Предмет " + SEQ.incrementAndGet())));
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
