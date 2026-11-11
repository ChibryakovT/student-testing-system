package ru.edu.testing.web;

import ru.edu.testing.domain.TestAttempt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Статус тестов для студента: лучший процент по каждому пройденному тесту. */
final class TestStatus {

    private TestStatus() {
    }

    /** Только опубликованные оценки: неопубликованные студент не видит. */
    static Map<Long, Integer> bestPercent(List<TestAttempt> attempts) {
        Map<Long, Integer> best = new HashMap<>();
        attempts.stream().filter(TestAttempt::isReleased).forEach(a -> best.merge(a.getTest().getId(), a.getPercent(), Math::max));
        return best;
    }

    /** Тесты, по которым есть результат на проверке у преподавателя. */
    static java.util.Set<Long> checking(List<TestAttempt> attempts) {
        return attempts.stream().filter(a -> !a.isReleased()).map(a -> a.getTest().getId())
                .collect(java.util.stream.Collectors.toSet());
    }
}
