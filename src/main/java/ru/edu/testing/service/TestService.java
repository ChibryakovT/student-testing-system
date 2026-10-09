package ru.edu.testing.service;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.Question;
import ru.edu.testing.domain.Test;
import ru.edu.testing.dto.TestForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.repository.TestRepository;
import ru.edu.testing.repository.UserRepository;
import ru.edu.testing.security.AppUserDetails;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class TestService {

    /** Белый список вариантов сортировки: произвольная строка из запроса в ORDER BY не попадает. */
    private static final Map<String, Sort> SORTS = Map.of(
            "title", Sort.by("title"),
            "new", Sort.by(Sort.Direction.DESC, "createdAt"),
            "old", Sort.by(Sort.Direction.ASC, "createdAt"),
            "time", Sort.by("timeLimitMinutes", "title"),
            "subject", Sort.by("subject.name", "title"));

    private final TestRepository testRepository;
    private final SubjectService subjectService;
    private final UserRepository userRepository;

    public TestService(TestRepository testRepository, SubjectService subjectService, UserRepository userRepository) {
        this.testRepository = testRepository;
        this.subjectService = subjectService;
        this.userRepository = userRepository;
    }

    public List<Test> search(String query, Long subjectId, String sort, AppUserDetails user) {
        return testRepository.search(Patterns.like(query), subjectId, user.isStudent(),
                SORTS.getOrDefault(sort, SORTS.get("new")));
    }

    /** Студент видит только опубликованные тесты; для него черновик «не существует». */
    public Test get(Long id, AppUserDetails user) {
        Test test = testRepository.findById(id).orElseThrow(() -> new NotFoundException("Тест не найден"));
        if (user.isStudent() && !test.isPublished()) {
            throw new NotFoundException("Тест не найден");
        }
        return test;
    }

    public Test getForEdit(Long id, AppUserDetails user) {
        Test test = testRepository.findById(id).orElseThrow(() -> new NotFoundException("Тест не найден"));
        checkCanEdit(test, user);
        return test;
    }

    @Transactional
    public Test create(TestForm form, AppUserDetails user) {
        Test test = new Test();
        test.setAuthor(userRepository.getReferenceById(user.getId()));
        apply(test, form);
        if (test.isPublished()) {
            throw new BusinessException("Нельзя опубликовать тест без вопросов. Сохраните черновик и добавьте вопросы");
        }
        return testRepository.save(test);
    }

    @Transactional
    public Test update(Long id, TestForm form, AppUserDetails user) {
        Test test = getForEdit(id, user);
        apply(test, form);
        if (test.isPublished()) {
            validateReadyForPublishing(test);
        }
        return test;
    }

    @Transactional
    public void delete(Long id, AppUserDetails user) {
        testRepository.delete(getForEdit(id, user));
    }

    public void checkCanEdit(Test test, AppUserDetails user) {
        boolean owner = test.getAuthor().getId().equals(user.getId());
        if (!(user.isAdmin() || (user.isStaff() && owner))) {
            throw new AccessDeniedException("Редактировать тест может только его автор или администратор");
        }
    }

    public boolean canEdit(Test test, AppUserDetails user) {
        return user.isAdmin() || (user.isStaff() && test.getAuthor().getId().equals(user.getId()));
    }

    public long countAll() {
        return testRepository.count();
    }

    static void validateReadyForPublishing(Test test) {
        if (test.getQuestions().isEmpty()) {
            throw new BusinessException("Нельзя опубликовать тест без вопросов");
        }
        for (Question q : test.getQuestions()) {
            if (q.getOptions().stream().noneMatch(o -> o.isCorrect())) {
                throw new BusinessException("У вопроса «" + q.getText() + "» не отмечен правильный ответ");
            }
        }
    }

    private void apply(Test test, TestForm form) {
        test.setTitle(form.getTitle().trim());
        test.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
        test.setSubject(subjectService.get(form.getSubjectId()));
        test.setTimeLimitMinutes(form.getTimeLimitMinutes());
        test.setPassingScore(form.getPassingScore());
        test.setPublished(form.isPublished());
    }
}
