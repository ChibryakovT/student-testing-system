package ru.edu.testing.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.edu.testing.security.AppUserDetails;
import ru.edu.testing.service.AttemptService;
import ru.edu.testing.service.ReportService;
import ru.edu.testing.service.TestService;

@Controller
public class HomeController {

    private final ReportService reportService;
    private final TestService testService;
    private final AttemptService attemptService;

    public HomeController(ReportService reportService, TestService testService, AttemptService attemptService) {
        this.reportService = reportService;
        this.testService = testService;
        this.attemptService = attemptService;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("summary", reportService.summary());
        model.addAttribute("availableTests", testService.search(null, null, "new", me).size());
        model.addAttribute("recentAttempts",
                attemptService.search(null, null, null, null, "date", me).stream().limit(5).toList());
        return "home";
    }
}
