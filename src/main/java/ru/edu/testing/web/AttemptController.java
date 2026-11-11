package ru.edu.testing.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.edu.testing.domain.AnswerOption;
import ru.edu.testing.domain.Question;
import ru.edu.testing.domain.TestAttempt;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.AttemptService;
import ru.edu.testing.service.SubjectService;
import ru.edu.testing.service.TestService;
import ru.edu.testing.service.UserService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/attempts")
public class AttemptController {

    private final AttemptService attemptService;
    private final SubjectService subjectService;
    private final TestService testService;
    private final UserService userService;

    public AttemptController(AttemptService attemptService, SubjectService subjectService, TestService testService,
                             UserService userService) {
        this.attemptService = attemptService;
        this.subjectService = subjectService;
        this.testService = testService;
        this.userService = userService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long testId,
                       @RequestParam(required = false) Long subjectId,
                       @RequestParam(required = false) Boolean passed,
                       @RequestParam(required = false) Long groupId,
                       @RequestParam(defaultValue = "date") String sort,
                       @RequestParam(defaultValue = "1") int page,
                       @AuthenticationPrincipal AppUserDetails me, Model model) {
        // Фильтр по группе доступен администратору
        Long groupFilter = me.isAdmin() ? groupId : null;
        Pager<TestAttempt> pager = Pager.of(attemptService.search(q, testId, subjectId, passed, groupFilter, sort, me), page);
        model.addAttribute("pager", pager);
        model.addAttribute("attempts", pager.items());
        if (me.isAdmin()) {
            model.addAttribute("groups", userService.groups());
        }
        model.addAttribute("groupId", groupFilter);
        model.addAttribute("subjects", testService.subjectsFor(me));
        model.addAttribute("tests", testService.search(null, null, "title", me));
        model.addAttribute("q", q);
        model.addAttribute("testId", testId);
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("passed", passed);
        model.addAttribute("sort", sort);
        return "attempts/list";
    }

    @GetMapping("/{id}")
    public String take(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        TestAttempt attempt = attemptService.getForTaking(id, me);
        LocalDateTime deadline = attempt.getStartedAt().plusMinutes(attempt.getTest().getTimeLimitMinutes());
        model.addAttribute("attempt", attempt);
        // Порядок вопросов и вариантов: при включённом перемешивании — свой у каждой попытки
        List<Question> questions = AttemptService.orderFor(attempt);
        Map<Long, List<AnswerOption>> options = new HashMap<>();
        questions.forEach(q -> options.put(q.getId(), AttemptService.optionsFor(attempt, q)));
        model.addAttribute("questions", questions);
        model.addAttribute("options", options);
        model.addAttribute("secondsLeft", Math.max(0, Duration.between(LocalDateTime.now(), deadline).getSeconds()));
        model.addAttribute("draft", AttemptService.draftOf(attempt));
        return "attempts/take";
    }

    /** Автосохранение: страница отправляет форму целиком при каждом изменении ответа. */
    @PostMapping("/{id}/draft")
    @ResponseBody
    public Map<String, String> draft(@PathVariable Long id, @RequestParam MultiValueMap<String, String> params,
                                     @AuthenticationPrincipal AppUserDetails me) {
        LocalDateTime saved = attemptService.saveDraft(id, parseAnswers(params), me);
        return Map.of("saved", saved.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id, @RequestParam MultiValueMap<String, String> params,
                         @AuthenticationPrincipal AppUserDetails me) {
        attemptService.submit(id, parseAnswers(params), me);
        return "redirect:/attempts/" + id + "/result";
    }

    @GetMapping("/{id}/result")
    public String result(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        TestAttempt attempt = attemptService.getResult(id, me);
        model.addAttribute("attempt", attempt);
        model.addAttribute("scoreVisible", AttemptService.scoreVisible(attempt, me));
        model.addAttribute("answersVisible", attemptService.answersVisible(attempt, me));
        if (me.isStudent()) {
            // «Пройти ещё раз» и «Следующий тест»
            model.addAttribute("attemptsLeft", attemptService.attemptsLeft(attempt.getTest(), me.getId()));
            model.addAttribute("nextAttemptAt", attemptService.nextAttemptAt(attempt.getTest(), me.getId()));
            Set<Long> done = new HashSet<>();
            attemptService.search(null, null, null, null, null, "date", me).forEach(a -> done.add(a.getTest().getId()));
            model.addAttribute("nextTest", testService.search(null, null, "new", me).stream()
                    .filter(t -> t.isPublished() && !done.contains(t.getId())).findFirst().orElse(null));
        }
        return "attempts/result";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                         RedirectAttributes redirect) {
        attemptService.delete(id, me);
        redirect.addFlashAttribute("success", "Результат удалён");
        return "redirect:/attempts";
    }

    /** Поля формы имеют вид q{idВопроса}=idВарианта. */
    static Map<Long, Set<Long>> parseAnswers(MultiValueMap<String, String> params) {
        Map<Long, Set<Long>> answers = new HashMap<>();
        params.forEach((key, values) -> {
            if (key.matches("q\\d{1,18}")) {
                Set<Long> options = new HashSet<>();
                for (String v : values) {
                    try {
                        options.add(Long.parseLong(v));
                    } catch (NumberFormatException e) {
                        throw new BusinessException("Некорректный ответ");
                    }
                }
                answers.put(Long.parseLong(key.substring(1)), options);
            }
        });
        return answers;
    }
}
