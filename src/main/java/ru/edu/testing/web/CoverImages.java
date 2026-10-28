package ru.edu.testing.web;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Обложка теста по названию дисциплины (фото с Wikimedia Commons, см. страницу «Фото и лицензии»).
 * В шаблонах: ${@covers.of(test.subject.name)}.
 */
@Component("covers")
public class CoverImages {

    private static final Map<String, String> BY_KEYWORD = new LinkedHashMap<>();

    static {
        BY_KEYWORD.put("баз", "/img/cover-db.jpg");
        BY_KEYWORD.put("sql", "/img/cover-db.jpg");
        BY_KEYWORD.put("сет", "/img/cover-network.jpg");
        BY_KEYWORD.put("безопас", "/img/cover-security.jpg");
        BY_KEYWORD.put("крипт", "/img/cover-security.jpg");
        BY_KEYWORD.put("программ", "/img/cover-code.jpg");
        BY_KEYWORD.put("java", "/img/cover-code.jpg");
        BY_KEYWORD.put("алгоритм", "/img/cover-code.jpg");
    }

    public String of(String subjectName) {
        String name = subjectName == null ? "" : subjectName.toLowerCase(Locale.ROOT);
        return BY_KEYWORD.entrySet().stream()
                .filter(e -> name.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("/img/cover-default.jpg");
    }
}
