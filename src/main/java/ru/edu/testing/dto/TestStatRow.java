package ru.edu.testing.dto;

/** Строка аналитического отчёта по тесту. */
public record TestStatRow(Long testId, String title, String subject, long attempts,
                          double averagePercent, int minPercent, int maxPercent, long passedCount) {

    public TestStatRow(Long testId, String title, String subject, Number attempts,
                       Number averagePercent, Number minPercent, Number maxPercent, Number passedCount) {
        this(testId, title, subject, attempts.longValue(), round(averagePercent.doubleValue()),
                minPercent.intValue(), maxPercent.intValue(), passedCount == null ? 0 : passedCount.longValue());
    }

    public int passRate() {
        return attempts == 0 ? 0 : (int) Math.round(passedCount * 100.0 / attempts);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
