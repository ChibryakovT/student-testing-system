package ru.edu.testing.web;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Разбивка списка на страницы. Ссылки сохраняют текущие фильтры и меняют только параметр page. */
public record Pager<T>(List<T> items, int page, int pages, int total, int from, int to) {

    public static final int SIZE = 50;

    public static <T> Pager<T> of(List<T> all, int page) {
        int pages = Math.max(1, (all.size() + SIZE - 1) / SIZE);
        int current = Math.min(Math.max(page, 1), pages);
        int from = (current - 1) * SIZE;
        int to = Math.min(from + SIZE, all.size());
        return new Pager<>(all.subList(from, to), current, pages, all.size(), all.isEmpty() ? 0 : from + 1, to);
    }

    public String url(int target) {
        return ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("page", target).build().toUriString();
    }

    /** Номера страниц для навигации: первая, последняя и соседние с текущей (0 — пропуск «…»). */
    public List<Integer> numbers() {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        for (int i = 1; i <= pages; i++) {
            if (i == 1 || i == pages || Math.abs(i - page) <= 2) {
                result.add(i);
            } else if (result.get(result.size() - 1) != 0) {
                result.add(0);
            }
        }
        return result;
    }
}
