package ru.edu.testing.web;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Время «по-человечески» для шаблонов: ${@time.ago(дата)} → «5 минут назад», «вчера в 21:32». */
@Component("time")
public class RelativeTime {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM 'в' HH:mm", RU);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", RU);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public String ago(LocalDateTime time) {
        return ago(time, LocalDateTime.now());
    }

    String ago(LocalDateTime time, LocalDateTime now) {
        if (time == null) {
            return "";
        }
        long minutes = Duration.between(time, now).toMinutes();
        if (minutes < 1 && minutes > -1) {
            return "только что";
        }
        if (minutes > 0 && minutes < 60) {
            return minutes + " " + plural(minutes, "минуту", "минуты", "минут") + " назад";
        }
        LocalDate day = time.toLocalDate();
        if (day.equals(now.toLocalDate())) {
            return "сегодня в " + time.format(HM);
        }
        if (day.equals(now.toLocalDate().minusDays(1))) {
            return "вчера в " + time.format(HM);
        }
        return day.getYear() == now.getYear() ? time.format(DAY) : time.format(DATE);
    }

    /** Точная дата для всплывающей подсказки. */
    public String full(LocalDateTime time) {
        return time == null ? "" : time.format(FULL);
    }

    static String plural(long n, String one, String few, String many) {
        long d = n % 100;
        if (d >= 11 && d <= 14) {
            return many;
        }
        return switch ((int) (n % 10)) {
            case 1 -> one;
            case 2, 3, 4 -> few;
            default -> many;
        };
    }
}
