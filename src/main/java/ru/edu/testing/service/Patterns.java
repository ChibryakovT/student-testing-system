package ru.edu.testing.service;

import java.util.Locale;

/** Шаблон для поиска через LIKE. Значение уходит в БД параметром запроса, поэтому SQL-инъекция исключена. */
final class Patterns {

    private Patterns() {
    }

    static String like(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }
        return "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
