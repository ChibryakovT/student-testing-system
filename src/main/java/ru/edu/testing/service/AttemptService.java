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
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AttemptService {

    /** Запас времени на отправку формы после истечения лимита. */
    private static final int GRACE_MINUTES = 1;

    private static final Map<String, Sort> SORTS = Map.of(
            "date", Sort.by(Sort.Direction.DESC, "finishedAt"),
            "score", Sort.by(Sort.Direction.DESC, "percent", "finishedAt"),
            "score_asc", Sort.by(Sort.Direction.ASC, "percent", "finishedAt"),
            "student", Sort.by("student.fullName", "finishedAt"),
            "test", Sort.by("test.title", "finishedAt"));

    private final TestAttemptRepository attemptRepository;
    private final TestService testService;
    private final UserRepository userRepository;
    private final CurriculumRepository curriculumRepository;

    public AttemptService(TestAttemptRepository attemptRepository, TestService testService,
                          UserRepository userRepository, CurriculumRepository curriculumRepository) {
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
                    TestAttempt attempt = new TestAttempt();
                    attempt.setTest(test);
                    attempt.setStudent(userRepository.getReferenceById(user.getId()));
                    attempt.setStartedAt(LocalDateTime.now());
                    attempt.setMaxScore(test.getMaxScore());
                    return attemptRepository.save(attempt);
                });
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

        Set<Long> knownQuestions = test.getQuestions().stream().map(Question::getId).collect(Collectors.toSet());
        for (Long questionId : answers.keySet()) {
            if (!knownQuestions.contains(questionId)) {
                throw new BusinessException("Вопрос " + questionId + " не относится к этому тесту");
            }
        }

        int score = 0;
        int maxScore = 0;
        for (Question question : test.getQuestions()) {
            maxScore += question.getPoints();
            Set<Long> selected = answers.getOrDefault(question.getId(), Set.of());
            Map<Long, AnswerOption> options = question.getOptions().stream()
                    .collect(Collectors.toMap(AnswerOption::getId, o -> o));
            if (!options.keySet().containsAll(selected)) {
                throw new BusinessException("Выбран вариант, не относящийся к вопросу " + question.getId());
            }
            if (question.getType() == QuestionType.SINGLE && selected.size() > 1) {
                throw new BusinessException("В вопросе с одним ответом можно выбрать только один вариант");
            }
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

        boolean overdue = now.isAfter(attempt.getStartedAt().plusMinutes(test.getTimeLimitMinutes() + GRACE_MINUTES));
        if (overdue) {
            // Время вышло: ответы сохраняются для истории, но баллы не начисляются.
            attempt.getAnswers().forEach(a -> a.setPointsEarned(0));
            score = 0;
        }

        attempt.setStatus(AttemptStatus.FINISHED);
        attempt.setFinishedAt(now);
        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setPercent(maxScore == 0 ? 0 : Math.round(score * 100f / maxScore));
        attempt.setPassed(attempt.getPercent() >= test.getPassingScore());
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
        return attemptRepository.search(studentId, teacherId, testId, subjectId, passed, groupId, Patterns.like(query),
                SORTS.getOrDefault(sort, SORTS.get("date")));
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
        attemptRepository.delete(attempt);
    }

    private TestAttempt find(Long id) {
        return attemptRepository.findById(id).orElseThrow(() -> new NotFoundException("Попытка не найдена"));
    }
}
