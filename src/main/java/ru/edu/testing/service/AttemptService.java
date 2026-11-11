package ru.edu.testing.service;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.*;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.repository.CurriculumRepository;
import ru.edu.testing.repository.TestAttemptRepository;
import ru.edu.testing.repository.UserRepository;
import ru.edu.testing.security.AppUserDetails;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AttemptService {

    /** Запас времени на отправку формы после истечения лимита. */
    private static final int GRACE_MINUTES = 1;
    private static final DateTimeFormatter NEXT_ATTEMPT = DateTimeFormatter.ofPattern("dd.MM в HH:mm");

    private static final Map<String, Sort> SORTS = Map.of(
            "date", Sort.by(Sort.Direction.DESC, "finishedAt"),
            "score", Sort.by(Sort.Direction.DESC, "percent", "finishedAt"),
            "score_asc", Sort.by(Sort.Direction.ASC, "percent", "finishedAt"),
            "student", Sort.by("student.fullName", "finishedAt"),
            "test", Sort.by("test.title", "finishedAt"));

    private final AuditService auditService;
    private final TestAttemptRepository attemptRepository;
    private final TestService testService;
    private final UserRepository userRepository;
    private final CurriculumRepository curriculumRepository;

    public AttemptService(TestAttemptRepository attemptRepository, TestService testService,
                          UserRepository userRepository, CurriculumRepository curriculumRepository, AuditService auditService) {
        this.auditService = auditService;
        this.attemptRepository = attemptRepository;
        this.testService = testService;
        this.userRepository = userRepository;
        this.curriculumRepository = curriculumRepository;
    }

    /** Начать прохождение. Если есть незавершённая попытка — продолжить её. */
    @Transactional
    public TestAttempt start(Long testId, AppUserDetails user) {
        if (!user.isStudent()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Проходить тесты могут только студенты");
        }
        Test test = testService.get(testId, user);
        if (!test.isPublished() || test.getQuestions().isEmpty()) {
            throw new BusinessException("Тест недоступен для прохождения");
        }
        return attemptRepository.findFirstByTestIdAndStudentIdAndStatus(testId, user.getId(), AttemptStatus.IN_PROGRESS)
                .orElseGet(() -> {
                    Integer left = attemptsLeft(test, user.getId());
                    if (left != null && left == 0) {
                        throw new BusinessException(HttpStatus.CONFLICT,
                                "Исчерпан лимит попыток: " + test.getMaxAttempts() + " из " + test.getMaxAttempts());
                    }
                    LocalDateTime next = nextAttemptAt(test, user.getId());
                    if (next != null) {
                        throw new BusinessException(HttpStatus.CONFLICT,
                                "Следующая попытка будет доступна " + next.format(NEXT_ATTEMPT));
                    }
                    TestAttempt attempt = new TestAttempt();
                    attempt.setTest(test);
                    attempt.setStudent(userRepository.getReferenceById(user.getId()));
                    attempt.setStartedAt(LocalDateTime.now());
                    attempt.setMaxScore(test.getMaxScore());
                    return attemptRepository.save(attempt);
                });
    }

    /** Сколько попыток осталось (null — без ограничений). */
    public Integer attemptsLeft(Test test, Long studentId) {
        if (test.getMaxAttempts() == null) {
            return null;
        }
        long used = attemptRepository.countByTestIdAndStudentIdAndStatus(test.getId(), studentId, AttemptStatus.FINISHED);
        return (int) Math.max(0, test.getMaxAttempts() - used);
    }

    /** Когда откроется следующая попытка, если действует пауза между попытками (null — уже можно). */
    public LocalDateTime nextAttemptAt(Test test, Long studentId) {
        if (test.getRetakeDelayMinutes() == null) {
            return null;
        }
        return attemptRepository.findFirstByTestIdAndStudentIdAndStatusOrderByFinishedAtDesc(
                        test.getId(), studentId, AttemptStatus.FINISHED)
                .map(a -> a.getFinishedAt().plusMinutes(test.getRetakeDelayMinutes()))
                .filter(next -> next.isAfter(LocalDateTime.now()))
                .orElse(null);
    }

    /** Студент видит оценку, если она опубликована; преподаватель и администратор — всегда. */
    public static boolean scoreVisible(TestAttempt attempt, AppUserDetails user) {
        return user.isStaff() || attempt.isReleased();
    }

    /** Правильные ответы и пояснения: по настройке теста и только вместе с оценкой. */
    public boolean answersVisible(TestAttempt attempt, AppUserDetails user) {
        if (user.isStaff()) {
            return true;
        }
        if (!attempt.isReleased()) {
            return false;
        }
        return switch (attempt.getTest().getShowAnswers()) {
            case AFTER_SUBMIT -> true;
            case AFTER_LAST_ATTEMPT -> {
                Integer left = attemptsLeft(attempt.getTest(), attempt.getStudent().getId());
                yield left != null && left == 0;
            }
            case NEVER -> false;
        };
    }

    /** Автосохранение ответов незавершённой попытки (принимается только до окончания времени). */
    @Transactional
    public LocalDateTime saveDraft(Long attemptId, Map<Long, Set<Long>> answers, AppUserDetails user) {
        TestAttempt attempt = getForTaking(attemptId, user);
        if (isOverdue(attempt, LocalDateTime.now())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Время на тест вышло");
        }
        validate(attempt.getTest(), answers);
        attempt.setDraft(answers.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> e.getKey() + ":" + e.getValue().stream().map(String::valueOf).collect(Collectors.joining(",")))
                .collect(Collectors.joining(";")));
        return LocalDateTime.now();
    }

    /** Автосохранённые ответы: вопрос → выбранные варианты. */
    public static Map<Long, Set<Long>> draftOf(TestAttempt attempt) {
        Map<Long, Set<Long>> answers = new HashMap<>();
        if (attempt.getDraft() == null || attempt.getDraft().isBlank()) {
            return answers;
        }
        for (String part : attempt.getDraft().split(";")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                answers.put(Long.parseLong(kv[0]), Arrays.stream(kv[1].split(","))
                        .map(Long::parseLong).collect(Collectors.toSet()));
            }
        }
        return answers;
    }

    /** Опубликовать оценки по тесту, если они выдаются после проверки. */
    @Transactional
    public int release(Long testId, AppUserDetails user) {
        Test test = testService.get(testId, user);
        if (!testService.canEdit(test, user)) {
            throw new AccessDeniedException("Публиковать оценки может автор теста или администратор");
        }
        int count = attemptRepository.releaseAll(testId);
        auditService.log("Оценки опубликованы", "«" + test.getTitle() + "»: " + count);
        return count;
    }

    public long pendingRelease(Long testId) {
        return attemptRepository.countByTestIdAndStatusAndReleasedFalse(testId, AttemptStatus.FINISHED);
    }

    private boolean isOverdue(TestAttempt attempt, LocalDateTime now) {
        return now.isAfter(attempt.getStartedAt().plusMinutes(attempt.getTest().getTimeLimitMinutes() + GRACE_MINUTES));
    }

    /** Вопросы и варианты должны относиться к тесту, в вопросе с одним ответом — не больше одного варианта. */
    private static void validate(Test test, Map<Long, Set<Long>> answers) {
        Map<Long, Question> questions = test.getQuestions().stream().collect(Collectors.toMap(Question::getId, q -> q));
        answers.forEach((questionId, selected) -> {
            Question question = questions.get(questionId);
            if (question == null) {
                throw new BusinessException("Вопрос " + questionId + " не относится к этому тесту");
            }
            Set<Long> known = question.getOptions().stream().map(AnswerOption::getId).collect(Collectors.toSet());
            if (!known.containsAll(selected)) {
                throw new BusinessException("Выбран вариант, не относящийся к вопросу " + questionId);
            }
            if (question.getType() == QuestionType.SINGLE && selected.size() > 1) {
                throw new BusinessException("В вопросе с одним ответом можно выбрать только один вариант");
            }
        });
    }

    /**
     * Порядок вопросов и вариантов для попытки. При включённом перемешивании порядок у каждой попытки свой,
     * но постоянный (зависит от номера попытки) — при обновлении страницы не меняется.
     */
    public static List<Question> orderFor(TestAttempt attempt) {
        List<Question> questions = new ArrayList<>(attempt.getTest().getQuestions());
        if (!attempt.getTest().isShuffle()) {
            return questions;
        }
        Random random = new Random(attempt.getId() * 7919L);
        Collections.shuffle(questions, random);
        return questions;
    }

    public static List<AnswerOption> optionsFor(TestAttempt attempt, Question question) {
        List<AnswerOption> options = new ArrayList<>(question.getOptions());
        if (attempt.getTest().isShuffle()) {
            Collections.shuffle(options, new Random(attempt.getId() * 31L + question.getId()));
        }
        return options;
    }

    /** Попытка, которую студент сейчас проходит (только своя и только незавершённая). */
    public TestAttempt getForTaking(Long attemptId, AppUserDetails user) {
        TestAttempt attempt = find(attemptId);
        if (!attempt.getStudent().getId().equals(user.getId())) {
            throw new NotFoundException("Попытка не найдена");
        }
        if (attempt.isFinished()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Тест уже завершён");
        }
        return attempt;
    }

    /**
     * Проверка ответов. Вопрос засчитывается, только если множество выбранных вариантов
     * полностью совпадает с множеством правильных.
     */
    @Transactional
    public TestAttempt submit(Long attemptId, Map<Long, Set<Long>> answers, AppUserDetails user) {
        TestAttempt attempt = getForTaking(attemptId, user);
        Test test = attempt.getTest();
        LocalDateTime now = LocalDateTime.now();
        boolean overdue = isOverdue(attempt, now);
        if (overdue) {
            // Время вышло: засчитываются только ответы, автосохранённые до окончания времени
            answers = draftOf(attempt);
            answers.keySet().retainAll(test.getQuestions().stream().map(Question::getId).collect(Collectors.toSet()));
        }
        validate(test, answers);

        int score = 0;
        int maxScore = 0;
        for (Question question : test.getQuestions()) {
            maxScore += question.getPoints();
            Set<Long> selected = answers.getOrDefault(question.getId(), Set.of());
            Map<Long, AnswerOption> options = question.getOptions().stream()
                    .collect(Collectors.toMap(AnswerOption::getId, o -> o));
            Set<Long> correct = question.getOptions().stream().filter(AnswerOption::isCorrect)
                    .map(AnswerOption::getId).collect(Collectors.toSet());
            boolean isCorrect = !selected.isEmpty() && selected.equals(correct);

            AttemptAnswer answer = new AttemptAnswer();
            answer.setAttempt(attempt);
            answer.setQuestion(question);
            answer.setCorrect(isCorrect);
            answer.setPointsEarned(isCorrect ? question.getPoints() : 0);
            selected.forEach(id -> answer.getSelectedOptions().add(options.get(id)));
            attempt.getAnswers().add(answer);
            score += answer.getPointsEarned();
        }

        attempt.setStatus(AttemptStatus.FINISHED);
        attempt.setFinishedAt(now);
        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setPercent(maxScore == 0 ? 0 : Math.round(score * 100f / maxScore));
        attempt.setPassed(attempt.getPercent() >= test.getPassingScore());
        attempt.setReleased(!test.isManualRelease());
        attempt.setDraft(null);
        auditService.log("Тест пройден", "«" + test.getTitle() + "»: " + attempt.getPercent() + " %"
                + (attempt.isPassed() ? ", сдан" : ", не сдан")
                + (overdue ? " (время вышло, засчитаны автосохранённые ответы)" : ""));
        return attempt;
    }

    /** Результат: студент видит только свои попытки, преподаватель и администратор — все. */
    public TestAttempt getResult(Long attemptId, AppUserDetails user) {
        TestAttempt attempt = find(attemptId);
        if (!canSee(attempt, user)) {
            throw new NotFoundException("Результат не найден");
        }
        if (!attempt.isFinished()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Тест ещё не завершён");
        }
        return attempt;
    }

    /** Студент — свои результаты; преподаватель — своих групп по своим дисциплинам; администратор — все. */
    public List<TestAttempt> search(String query, Long testId, Long subjectId, Boolean passed, Long groupId,
                                    String sort, AppUserDetails user) {
        Long studentId = user.isStudent() ? user.getId() : null;
        Long teacherId = user.isStaff() && !user.isAdmin() ? user.getId() : null;
        List<TestAttempt> found = attemptRepository.search(studentId, teacherId, testId, subjectId, passed, groupId,
                Patterns.like(query), SORTS.getOrDefault(sort, SORTS.get("date")));
        // Фильтр и сортировка по оценке не должны выдавать студенту неопубликованные результаты
        if (user.isStudent() && (passed != null || String.valueOf(sort).startsWith("score"))) {
            return found.stream().filter(TestAttempt::isReleased).toList();
        }
        return found;
    }

    /** Право видеть попытку: своя (студент), своей группы по своей дисциплине или свой тест (преподаватель). */
    boolean canSee(TestAttempt attempt, AppUserDetails user) {
        if (user.isAdmin()) {
            return true;
        }
        if (user.isStudent()) {
            return attempt.getStudent().getId().equals(user.getId());
        }
        Test test = attempt.getTest();
        if (test.getAuthor().getId().equals(user.getId())) {
            return true;
        }
        StudyGroup group = attempt.getStudent().getGroup();
        return group != null && curriculumRepository.existsByTeacherIdAndGroupIdAndSubjectId(
                user.getId(), group.getId(), test.getSubject().getId());
    }

    @Transactional
    public void delete(Long attemptId, AppUserDetails user) {
        TestAttempt attempt = find(attemptId);
        if (!user.isStaff() || !canSee(attempt, user)) {
            throw new AccessDeniedException("Удалять результаты может только преподаватель группы");
        }
        auditService.log("Результат удалён", attempt.getStudent().getFullName() + ", «" + attempt.getTest().getTitle()
                + "» — " + attempt.getPercent() + " %");
        attemptRepository.delete(attempt);
    }

    private TestAttempt find(Long id) {
        return attemptRepository.findById(id).orElseThrow(() -> new NotFoundException("Попытка не найдена"));
    }
}
