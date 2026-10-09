package ru.edu.testing.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.edu.testing.domain.TestAttempt;
import ru.edu.testing.dto.ApiDtos.AnswerRequest;
import ru.edu.testing.dto.ApiDtos.AttemptDto;
import ru.edu.testing.dto.ApiDtos.QuestionDto;
import ru.edu.testing.dto.ApiDtos.StartedAttemptDto;
import ru.edu.testing.dto.ApiDtos.SubmitRequest;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.AttemptService;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AttemptApiController {

    private final AttemptService attemptService;

    public AttemptApiController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    /** Начало прохождения: вопросы отдаются без признака правильного ответа. */
    @PostMapping("/tests/{testId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public StartedAttemptDto start(@PathVariable Long testId, @AuthenticationPrincipal AppUserDetails me) {
        TestAttempt attempt = attemptService.start(testId, me);
        List<QuestionDto> questions = attempt.getTest().getQuestions().stream()
                .map(q -> QuestionDto.of(q, false)).toList();
        return new StartedAttemptDto(attempt.getId(), attempt.getTest().getTitle(),
                attempt.getTest().getTimeLimitMinutes(), attempt.getStartedAt(), questions);
    }

    @PostMapping("/attempts/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptDto submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest request,
                             @AuthenticationPrincipal AppUserDetails me) {
        Map<Long, Set<Long>> answers = request.answers().stream().collect(Collectors.toMap(
                AnswerRequest::questionId,
                a -> a.optionIds() == null ? Set.of() : new HashSet<>(a.optionIds()),
                (a, b) -> a));
        return AttemptDto.of(attemptService.submit(id, answers, me));
    }

    @GetMapping("/attempts/{id}")
    public AttemptDto get(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return AttemptDto.of(attemptService.getResult(id, me));
    }

    /** Студент получает только свои результаты; фильтры: q, testId, subjectId, passed; sort = date|score|score_asc|student|test. */
    @GetMapping("/attempts")
    public List<AttemptDto> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Long testId,
                                 @RequestParam(required = false) Long subjectId,
                                 @RequestParam(required = false) Boolean passed,
                                 @RequestParam(defaultValue = "date") String sort,
                                 @AuthenticationPrincipal AppUserDetails me) {
        return attemptService.search(q, testId, subjectId, passed, sort, me).stream().map(AttemptDto::of).toList();
    }

    @DeleteMapping("/attempts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        attemptService.delete(id, me);
    }
}
