package ru.edu.testing.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.edu.testing.domain.TestAttempt;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.AttemptService;
import ru.edu.testing.service.SubjectService;
import ru.edu.testing.service.TestService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/attempts")
public class AttemptController {

    private final AttemptService attemptService;
    private final SubjectService subjectService;
    private final TestService testService;

    public AttemptController(AttemptService attemptService, SubjectService subjectService, TestService testService) {
        this.attemptService = attemptService;
        this.subjectService = subjectService;
        this.testService = testService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long testId,
                       @RequestParam(required = false) Long subjectId,
                       @RequestParam(required = false) Boolean passed,
                       @RequestParam(defaultValue = "date") String sort,
                       @AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("attempts", attemptService.search(q, testId, subjectId, passed, sort, me));
        model.addAttribute("subjects", subjectService.findAll(null));
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
        model.addAttribute("secondsLeft", Math.max(0, Duration.between(LocalDateTime.now(), deadline).getSeconds()));
        return "attempts/take";
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id, @RequestParam MultiValueMap<String, String> params,
                         @AuthenticationPrincipal AppUserDetails me) {
        attemptService.submit(id, parseAnswers(params), me);
        return "redirect:/attempts/" + id + "/result";
    }

    @GetMapping("/{id}/result")
    public String result(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("attempt", attemptService.getResult(id, me));
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
