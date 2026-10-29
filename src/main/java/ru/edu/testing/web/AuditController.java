package ru.edu.testing.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.edu.testing.service.AuditService;

/** Журнал действий. Доступ: администратор (см. SecurityConfig). */
@Controller
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(required = false) String q, @RequestParam(required = false) String action,
                        Model model) {
        model.addAttribute("entries", auditService.search(q, action, 300));
        model.addAttribute("actions", auditService.actions());
        model.addAttribute("q", q);
        model.addAttribute("action", action);
        return "audit";
    }
}
