package ru.edu.testing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.AnswerOption;
import ru.edu.testing.domain.Question;
import ru.edu.testing.domain.QuestionType;
import ru.edu.testing.domain.Test;
import ru.edu.testing.dto.OptionForm;
import ru.edu.testing.dto.QuestionForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.repository.QuestionRepository;
import ru.edu.testing.security.AppUserDetails;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class QuestionService {

    private final AuditService auditService;
    private final QuestionRepository questionRepository;
    private final TestService testService;

    public QuestionService(QuestionRepository questionRepository, TestService testService, AuditService auditService) {
        this.auditService = auditService;
        this.questionRepository = questionRepository;
        this.testService = testService;
    }

    public Question getForEdit(Long id, AppUserDetails user) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Вопрос не найден"));
        testService.checkCanEdit(question.getTest(), user);
        return question;
    }

    @Transactional
    public Question add(Long testId, QuestionForm form, AppUserDetails user) {
        Test test = testService.getForEdit(testId, user);
        Question question = new Question();
        question.setTest(test);
        question.setPosition(test.getQuestions().size() + 1);
        apply(question, form);
        test.getQuestions().add(question);
        Question saved = questionRepository.save(question);
        auditService.log("Вопрос добавлен", "Тест «" + test.getTitle() + "»: " + saved.getText());
        return saved;
    }

    @Transactional
    public Question update(Long id, QuestionForm form, AppUserDetails user) {
        Question question = getForEdit(id, user);
        apply(question, form);
        auditService.log("Вопрос изменён", "Тест «" + question.getTest().getTitle() + "»: " + question.getText());
        return question;
    }

    @Transactional
    public Long delete(Long id, AppUserDetails user) {
        Question question = getForEdit(id, user);
        Test test = question.getTest();
        auditService.log("Вопрос удалён", "Тест «" + test.getTitle() + "»: " + question.getText());
        test.getQuestions().remove(question);
        if (test.isPublished() && test.getQuestions().isEmpty()) {
            test.setPublished(false);
        }
        return test.getId();
    }

    public static QuestionForm toForm(Question question) {
        QuestionForm form = new QuestionForm();
        form.setText(question.getText());
        form.setType(question.getType());
        form.setPoints(question.getPoints());
        form.setExplanation(question.getExplanation());
        form.setOptions(new java.util.ArrayList<>(question.getOptions().stream()
                .map(o -> new OptionForm(o.getText(), o.isCorrect())).toList()));
        return form.padOptions();
    }

    /** Проверка корректности вопроса: минимум 2 варианта и правильный ответ в соответствии с типом. */
    private static void apply(Question question, QuestionForm form) {
        List<OptionForm> filled = form.getOptions().stream()
                .filter(o -> o.getText() != null && !o.getText().isBlank())
                .toList();
        if (filled.size() < 2) {
            throw new BusinessException("Добавьте минимум два варианта ответа");
        }
        long correct = filled.stream().filter(OptionForm::isCorrect).count();
        if (correct == 0) {
            throw new BusinessException("Отметьте хотя бы один правильный вариант");
        }
        if (form.getType() == QuestionType.SINGLE && correct > 1) {
            throw new BusinessException("Для вопроса с одним ответом отметьте ровно один правильный вариант");
        }
        question.setText(form.getText().trim());
        question.setType(form.getType());
        question.setPoints(form.getPoints());
        question.setExplanation(form.getExplanation() == null || form.getExplanation().isBlank()
                ? null : form.getExplanation().trim());
        question.getOptions().clear();
        filled.forEach(o -> question.addOption(new AnswerOption(o.getText().trim(), o.isCorrect())));
    }
}
