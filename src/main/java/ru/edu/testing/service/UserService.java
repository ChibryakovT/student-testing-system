package ru.edu.testing.service;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.Role;
import ru.edu.testing.domain.StudyGroup;
import ru.edu.testing.repository.DepartmentRepository;
import ru.edu.testing.repository.StudyGroupRepository;
import ru.edu.testing.domain.User;
import ru.edu.testing.dto.RegistrationForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Map<String, Sort> SORTS = Map.of(
            "name", Sort.by("fullName"),
            "username", Sort.by("username"),
            "role", Sort.by("role", "fullName"),
            "created", Sort.by(Sort.Direction.DESC, "createdAt"));

    private final AuditService auditService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudyGroupRepository groupRepository;
    private final DepartmentRepository departmentRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       StudyGroupRepository groupRepository, DepartmentRepository departmentRepository, AuditService auditService) {
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.groupRepository = groupRepository;
        this.departmentRepository = departmentRepository;
    }

    /** Самостоятельная регистрация всегда создаёт студента: роль из запроса не принимается. */
    @Transactional
    public User register(RegistrationForm form) {
        if (!form.isConsent()) {
            throw new BusinessException("Для регистрации нужно согласие на обработку персональных данных");
        }
        User user = create(form, Role.STUDENT);
        user.setConsentAt(LocalDateTime.now());
        return user;
    }

    @Transactional
    public User create(RegistrationForm form, Role role) {
        if (form.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            // BCrypt учитывает только первые 72 байта пароля (кириллический символ занимает 2 байта)
            throw new BusinessException("Пароль слишком длинный");
        }
        String username = form.getUsername().trim();
        String email = form.getEmail().trim().toLowerCase();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Логин «" + username + "» уже занят");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Этот e-mail уже зарегистрирован");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setFullName(form.getFullName().trim());
        user.setEmail(email);
        user.setRole(role);
        if (role == Role.STUDENT) {
            if (form.getGroupId() == null) {
                throw new BusinessException("Выберите учебную группу");
            }
            user.setGroup(groupRepository.findById(form.getGroupId())
                    .orElseThrow(() -> new BusinessException("Учебная группа не найдена")));
        }
        if (role == Role.TEACHER && form instanceof ru.edu.testing.dto.UserForm uf && uf.getDepartmentId() != null) {
            user.setDepartment(departmentRepository.findById(uf.getDepartmentId())
                    .orElseThrow(() -> new BusinessException("Кафедра не найдена")));
        }
        user.setRecordBook(blankToNull(form.getRecordBook()));
        User saved = userRepository.save(user);
        auditService.log("Пользователь создан", saved.getUsername() + " — " + role.getTitle() + ", " + saved.getFullName());
        if (role == Role.STUDENT && saved.getRecordBook() == null) {
            // Номер зачётки не указан — формируется автоматически: год поступления + 05 + id
            saved.setRecordBook(String.format("%d05%04d", java.time.Year.now().getValue(), saved.getId()));
        }
        return saved;
    }

    public List<User> search(String query, Role role, Long groupId, String sort) {
        return userRepository.search(Patterns.like(query), role, groupId, SORTS.getOrDefault(sort, SORTS.get("name")));
    }

    /** Учебные группы для фильтров и форм. */
    public List<StudyGroup> groups() {
        return groupRepository.findAllOrdered();
    }

    public User get(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    /** Смена пароля самим пользователем: нужен текущий пароль, новый не должен совпадать со старым. */
    @Transactional
    public void changePassword(Long userId, String current, String newPassword, String confirm) {
        User user = get(userId);
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new BusinessException("Текущий пароль указан неверно");
        }
        if (!newPassword.equals(confirm)) {
            throw new BusinessException("Новый пароль и повтор не совпадают");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException("Новый пароль совпадает с текущим");
        }
        if (newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("Пароль слишком длинный");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        auditService.log("Пароль изменён", user.getUsername());
    }

    @Transactional
    public User update(Long id, Role role, boolean enabled, Long currentUserId) {
        User user = get(id);
        if (user.getId().equals(currentUserId) && (role != Role.ADMIN || !enabled)) {
            throw new BusinessException("Нельзя снять с себя роль администратора или заблокировать себя");
        }
        user.setRole(role);
        user.setEnabled(enabled);
        auditService.log("Пользователь изменён", user.getUsername() + ": " + role.getTitle() + (enabled ? ", активен" : ", заблокирован"));
        return user;
    }

    @Transactional
    public void delete(Long id, Long currentUserId) {
        User user = get(id);
        if (user.getId().equals(currentUserId)) {
            throw new BusinessException("Нельзя удалить собственную учётную запись");
        }
        auditService.forget(user.getUsername(), user.getFullName(), "[удалён #" + id + "]");
        auditService.log("Пользователь удалён", "#" + id);
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "У пользователя есть созданные тесты — удалите их или заблокируйте пользователя");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
