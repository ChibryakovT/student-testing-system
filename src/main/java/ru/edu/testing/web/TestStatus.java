package ru.edu.testing.web;

import ru.edu.testing.domain.TestAttempt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Статус тестов для студента: лучший процент по каждому пройденному тесту. */
final class TestStatus {

    private TestStatus() {
    }

    static Map<Long, Integer> bestPercent(List<TestAttempt> attempts) {
        Map<Long, Integer> best = new HashMap<>();
        attempts.forEach(a -> best.merge(a.getTest().getId(), a.getPercent(), Math::max));
        return best;
    }
}
