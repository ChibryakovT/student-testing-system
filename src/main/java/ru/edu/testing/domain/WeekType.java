package ru.edu.testing.domain;

/** Неделя занятия: каждую неделю, только по числителю (нечётные) или по знаменателю (чётные). */
public enum WeekType {
    EVERY("каждую неделю", ""),
    NUMERATOR("числитель", "числ."),
    DENOMINATOR("знаменатель", "знам.");

    private final String title;
    private final String shortTitle;

    WeekType(String title, String shortTitle) {
        this.title = title;
        this.shortTitle = shortTitle;
    }

    public String getTitle() { return title; }
    public String getShortTitle() { return shortTitle; }

    /** Идёт ли занятие на неделе с данной чётностью (числитель = true). */
    public boolean matches(boolean numeratorWeek) {
        return this == EVERY || (this == NUMERATOR) == numeratorWeek;
    }
}
