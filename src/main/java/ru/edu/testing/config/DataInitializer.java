package ru.edu.testing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.Role;
import ru.edu.testing.dto.RegistrationForm;
import ru.edu.testing.repository.UserRepository;
import ru.edu.testing.service.UserService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;

/**
 * Создаёт администратора при первом запуске и (опционально) заполняет базу данными вуза.
 * Пароли берутся из переменных окружения; если они не заданы — генерируются случайно
 * и однократно выводятся в лог. В исходном коде пароли не хранятся.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final UniversitySeeder seeder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;
    @Value("${app.admin.password:}")
    private String adminPassword;
    @Value("${app.demo.enabled:false}")
    private boolean demoEnabled;
    @Value("${app.demo.password:}")
    private String demoPassword;
    @Value("${app.demo.teacher-password:}")
    private String teacherPassword;
    @Value("${app.seed.file:data/university.json}")
    private String seedFile;

    public DataInitializer(UserRepository userRepository, UserService userService, UniversitySeeder seeder) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.seeder = seeder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (userRepository.count() > 0) {
            return;
        }
        RegistrationForm admin = new RegistrationForm();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordOrRandom(adminPassword, "ADMIN_PASSWORD"));
        admin.setFullName("Администратор системы");
        admin.setEmail("admin@rtsu-demo.example");
        userService.create(admin, Role.ADMIN);
        log.info("Создан администратор «{}»", adminUsername);
        if (!demoEnabled) {
            return;
        }
        List<String[]> accounts = seeder.seed(seeder.load(seedFile),
                passwordOrRandom(demoPassword, "DEMO_PASSWORD"),
                passwordOrRandom(teacherPassword, "DEMO_TEACHER_PASSWORD"));
        writeAccounts(accounts);
    }

    /** Список логинов (без паролей) — в data/accounts.csv, папка data не хранится в Git. */
    private void writeAccounts(List<String[]> accounts) {
        StringBuilder sb = new StringBuilder("﻿Роль;ФИО;Логин;Группа / кафедра\n");
        accounts.forEach(a -> sb.append(String.join(";", a)).append('\n'));
        try {
            Path file = Path.of("data", "accounts.csv");
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            log.info("Список учётных записей сохранён в {}", file.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Не удалось сохранить список учётных записей: {}", e.getMessage());
        }
    }

    private static String passwordOrRandom(String configured, String envName) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        log.warn("Переменная {} не задана. Сгенерирован пароль: {} (сохраните его, он больше не будет показан)",
                envName, sb);
        return sb.toString();
    }
}
