package ru.edu.testing.dto;

/** Сравнение групп: число попыток, средний процент и доля сдавших. */
public record GroupStatRow(Long groupId, String group, long attempts, double averagePercent, long passedCount) {

    public GroupStatRow {
        averagePercent = Math.round(averagePercent * 10) / 10.0;
    }

    public GroupStatRow(Long groupId, String group, Number attempts, Number averagePercent, Number passedCount) {
        this(groupId, group, attempts.longValue(), averagePercent.doubleValue(),
                passedCount == null ? 0 : passedCount.longValue());
    }

    public int passRate() {
        return attempts == 0 ? 0 : (int) Math.round(passedCount * 100.0 / attempts);
    }
}
