package ru.edu.testing.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebHelpersTest {

    private final RelativeTime time = new RelativeTime();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 11, 15, 30);

    @Test
    @DisplayName("Время по-человечески: минуты, сегодня, вчера, дата")
    void relativeTime() {
        assertEquals("только что", time.ago(now.minusSeconds(20), now));
        assertEquals("1 минуту назад", time.ago(now.minusMinutes(1), now));
        assertEquals("3 минуты назад", time.ago(now.minusMinutes(3), now));
        assertEquals("12 минут назад", time.ago(now.minusMinutes(12), now));
        assertEquals("21 минуту назад", time.ago(now.minusMinutes(21), now));
        assertEquals("сегодня в 09:05", time.ago(now.withHour(9).withMinute(5), now));
        assertEquals("вчера в 21:32", time.ago(now.minusDays(1).withHour(21).withMinute(32), now));
        assertEquals("9 ноября в 21:32", time.ago(now.minusDays(2).withHour(21).withMinute(32), now));
        assertEquals("9 ноября 2025", time.ago(now.minusYears(1).minusDays(2), now));
    }

    @Test
    @DisplayName("Разбивка на страницы: границы, номер страницы вне диапазона, пропуски «…»")
    void pager() {
        List<Integer> all = IntStream.rangeClosed(1, 134).boxed().toList();
        Pager<Integer> second = Pager.of(all, 2);
        assertEquals(50, second.items().size());
        assertEquals(51, second.from());
        assertEquals(3, second.pages());
        assertEquals(34, Pager.of(all, 99).items().size());
        assertEquals(1, Pager.of(List.of(), 5).page());
        assertEquals(List.of(1, 0, 4, 5, 6, 7, 8, 0, 20),
                Pager.of(IntStream.rangeClosed(1, 1000).boxed().toList(), 6).numbers());
    }
}
