package ru.edu.testing.dto;

/** Рейтинг студентов по среднему результату. */
public record StudentRankRow(Long studentId, String fullName, String group, long attempts, double averagePercent,
                             int bestPercent) {

    public StudentRankRow {
        averagePercent = Math.round(averagePercent * 10) / 10.0;
    }

    public StudentRankRow(Long studentId, String fullName, String group, Number attempts, Number averagePercent,
                          Number bestPercent) {
        this(studentId, fullName, group, attempts.longValue(), averagePercent.doubleValue(), bestPercent.intValue());
    }
}
