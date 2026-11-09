package ru.edu.testing.service;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.AuditEntry;
import ru.edu.testing.repository.AuditRepository;

import java.util.List;

/**
 * Журнал действий. Записываются изменения данных (тесты, вопросы, расписание, учебный план, пользователи)
 * и входы в систему. Действия без авторизованного пользователя (начальное заполнение базы) не пишутся.
 */
@Service
public class AuditService {

    public static final String LOGIN = "Вход";
    public static final String LOGIN_FAILED = "Неудачный вход";

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /** Записать действие текущего пользователя (в той же транзакции, что и само изменение). */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void log(String action, String details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return;
        }
        auditRepository.save(new AuditEntry(auth.getName(), action, details));
    }

    /** Вход через форму на сайте (запросы API с HTTP Basic не засоряют журнал). */
    @EventListener
    @Transactional
    public void onLogin(InteractiveAuthenticationSuccessEvent event) {
        auditRepository.save(new AuditEntry(event.getAuthentication().getName(), LOGIN, null));
    }

    @EventListener
    @Transactional
    public void onLoginFailure(AbstractAuthenticationFailureEvent event) {
        String name = String.valueOf(event.getAuthentication().getPrincipal());
        auditRepository.save(new AuditEntry(name.length() > 50 ? name.substring(0, 50) : name, LOGIN_FAILED,
                event.getException().getClass().getSimpleName().replace("Exception", "")));
    }

    /** Обезличить записи удаляемого пользователя: после удаления его логин не должен оставаться в журнале. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void forget(String username, String fullName, String replacement) {
        auditRepository.replaceUsername(username, replacement);
        auditRepository.replaceInDetails(username, replacement);
        auditRepository.replaceInDetails(fullName, replacement);
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> search(String query, String action, int limit) {
        return auditRepository.search(Patterns.like(query), action == null || action.isBlank() ? null : action,
                PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<String> actions() {
        return auditRepository.actions();
    }
}
