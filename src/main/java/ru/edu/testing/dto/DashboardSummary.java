package ru.edu.testing.dto;

public record DashboardSummary(long students, long teachers, long tests, long publishedTests,
                               long finishedAttempts, long passedAttempts, double averagePercent) {

    public int passRate() {
        return finishedAttempts == 0 ? 0 : (int) Math.round(passedAttempts * 100.0 / finishedAttempts);
    }
}
