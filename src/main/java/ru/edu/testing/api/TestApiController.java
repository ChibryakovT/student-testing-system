package ru.edu.testing.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.edu.testing.domain.Test;
import ru.edu.testing.dto.ApiDtos.QuestionDto;
import ru.edu.testing.dto.ApiDtos.TestDto;
import ru.edu.testing.dto.QuestionForm;
import ru.edu.testing.dto.TestForm;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.QuestionService;
import ru.edu.testing.service.TestService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TestApiController {

    private final TestService testService;
    private final QuestionService questionService;

    public TestApiController(TestService testService, QuestionService questionService) {
        this.testService = testService;
        this.questionService = questionService;
    }

    /** Поиск (q), фильтр по дисциплине (subjectId) и сортировка (sort = title|new|old|time|subject). */
    @GetMapping("/tests")
    public List<TestDto> list(@RequestParam(required = false) String q,
                              @RequestParam(required = false) Long subjectId,
                              @RequestParam(defaultValue = "new") String sort,
                              @AuthenticationPrincipal AppUserDetails me) {
        return testService.search(q, subjectId, sort, me).stream().map(TestDto::of).toList();
    }

    @GetMapping("/tests/{id}")
    public TestDto get(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return TestDto.of(testService.get(id, me));
    }

    /** Вопросы с правильными ответами доступны только автору теста и администратору. */
    @GetMapping("/tests/{id}/questions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<QuestionDto> questions(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        Test test = testService.getForEdit(id, me);
        return test.getQuestions().stream().map(q -> QuestionDto.of(q, true)).toList();
    }

    @PostMapping("/tests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public TestDto create(@Valid @RequestBody TestForm form, @AuthenticationPrincipal AppUserDetails me) {
        return TestDto.of(testService.create(form, me));
    }

    @PutMapping("/tests/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public TestDto update(@PathVariable Long id, @Valid @RequestBody TestForm form,
                          @AuthenticationPrincipal AppUserDetails me) {
        return TestDto.of(testService.update(id, form, me));
    }

    @DeleteMapping("/tests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        testService.delete(id, me);
    }

    @PostMapping("/tests/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public QuestionDto addQuestion(@PathVariable Long id, @Valid @RequestBody QuestionForm form,
                                   @AuthenticationPrincipal AppUserDetails me) {
        return QuestionDto.of(questionService.add(id, form, me), true);
    }

    @PutMapping("/questions/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public QuestionDto updateQuestion(@PathVariable Long id, @Valid @RequestBody QuestionForm form,
                                      @AuthenticationPrincipal AppUserDetails me) {
        return QuestionDto.of(questionService.update(id, form, me), true);
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public void deleteQuestion(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        questionService.delete(id, me);
    }
}
