package ru.edu.testing.service;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import ru.edu.testing.domain.CurriculumItem;
import ru.edu.testing.domain.Question;
import ru.edu.testing.domain.StudyGroup;
import ru.edu.testing.domain.Subject;
import ru.edu.testing.domain.Test;
import ru.edu.testing.domain.User;
import ru.edu.testing.repository.CurriculumRepository;
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

    private final AuditService auditService;
    private final TestRepository testRepository;
    private final SubjectService subjectService;
    private final UserRepository userRepository;
    private final CurriculumRepository curriculumRepository;

    public TestService(TestRepository testRepository, SubjectService subjectService, UserRepository userRepository,
                       CurriculumRepository curriculumRepository, AuditService auditService) {
        this.auditService = auditService;
        this.testRepository = testRepository;
        this.subjectService = subjectService;
        this.userRepository = userRepository;
        this.curriculumRepository = curriculumRepository;
    }

    /**
     * Каталог с учётом учебного плана: студент видит опубликованные тесты по дисциплинам своей группы,
     * преподаватель — свои тесты и тесты по дисциплинам, которые он ведёт, администратор — все.
     */
    public List<Test> search(String query, Long subjectId, String sort, AppUserDetails user) {
        String pattern = Patterns.like(query);
        Sort order = SORTS.getOrDefault(sort, SORTS.get("new"));
        if (user.isStudent()) {
            return testRepository.searchForStudent(pattern, subjectId, user.getId(), order);
        }
        if (user.isAdmin()) {
            return testRepository.search(pattern, subjectId, order);
        }
        return testRepository.searchForTeacher(pattern, subjectId, user.getId(), order);
    }

    /** Тест, недоступный пользователю по учебному плану, для него «не существует». */
    public Test get(Long id, AppUserDetails user) {
        Test test = testRepository.findById(id).orElseThrow(() -> new NotFoundException("Тест не найден"));
        if (!canView(test, user)) {
            throw new NotFoundException("Тест не найден");
        }
        return test;
    }

    public boolean canView(Test test, AppUserDetails user) {
        if (user.isAdmin()) {
            return true;
        }
        Long subjectId = test.getSubject().getId();
        if (user.isStudent()) {
            Long groupId = groupOf(user);
            return test.isPublished() && groupId != null && curriculumRepository.existsByGroupIdAndSubjectId(groupId, subjectId);
        }
        return test.getAuthor().getId().equals(user.getId())
                || curriculumRepository.existsByTeacherIdAndSubjectId(user.getId(), subjectId);
    }

    /** Дисциплины, доступные пользователю: в фильтрах каталога и в форме теста. */
    public List<Subject> subjectsFor(AppUserDetails user) {
        if (user.isAdmin()) {
            return subjectService.findAll(null);
        }
        if (user.isStudent()) {
            Long groupId = groupOf(user);
            return groupId == null ? List.of()
                    : curriculumRepository.findByGroup(groupId).stream().map(CurriculumItem::getSubject).toList();
        }
        return curriculumRepository.findSubjectsTaughtBy(user.getId());
    }

    /** Форма контроля по дисциплинам из учебного плана: группы студента или групп преподавателя. */
    public Map<Long, String> controlForms(AppUserDetails user) {
        List<CurriculumItem> plan;
        if (user.isStudent()) {
            Long groupId = groupOf(user);
            plan = groupId == null ? List.of() : curriculumRepository.findByGroup(groupId);
        } else if (user.isAdmin()) {
            plan = List.of();
        } else {
            plan = curriculumRepository.findByTeacher(user.getId());
        }
        Map<Long, String> forms = new java.util.HashMap<>();
        plan.forEach(c -> forms.putIfAbsent(c.getSubject().getId(), c.getControlForm().getTitle()));
        return forms;
    }

    private Long groupOf(AppUserDetails user) {
        return userRepository.findById(user.getId()).map(User::getGroup).map(StudyGroup::getId).orElse(null);
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
        apply(test, form, user);
        if (test.isPublished()) {
            throw new BusinessException("Нельзя опубликовать тест без вопросов. Сохраните черновик и добавьте вопросы");
        }
        Test saved = testRepository.save(test);
        auditService.log("Тест создан", "«" + saved.getTitle() + "» (" + saved.getSubject().getName() + ")");
        return saved;
    }

    @Transactional
    public Test update(Long id, TestForm form, AppUserDetails user) {
        Test test = getForEdit(id, user);
        apply(test, form, user);
        if (test.isPublished()) {
            validateReadyForPublishing(test);
        }
        auditService.log("Тест изменён", "«" + test.getTitle() + "»" + (test.isPublished() ? ", опубликован" : ", черновик"));
        return test;
    }

    @Transactional
    public void delete(Long id, AppUserDetails user) {
        Test test = getForEdit(id, user);
        auditService.log("Тест удалён", "«" + test.getTitle() + "»");
        testRepository.delete(test);
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

    private void apply(Test test, TestForm form, AppUserDetails user) {
        if (!user.isAdmin() && !curriculumRepository.existsByTeacherIdAndSubjectId(user.getId(), form.getSubjectId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "По учебному плану вы не ведёте эту дисциплину — выберите одну из своих дисциплин");
        }
        test.setTitle(form.getTitle().trim());
        test.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
        test.setSubject(subjectService.get(form.getSubjectId()));
        test.setTimeLimitMinutes(form.getTimeLimitMinutes());
        test.setPassingScore(form.getPassingScore());
        test.setPublished(form.isPublished());
        test.setShuffle(form.isShuffle());
        test.setMaxAttempts(form.getMaxAttempts());
    }
}
