package ru.edu.testing.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Готовая ведомость: строки, итоги и выбранные колонки. */
public record GradeSheet(SheetForm form, String testTitle, List<SheetColumn> columns, List<Row> rows,
                         Map<String, Long> totals) {

    /** Строка ведомости. grade == null — студент не проходил тест. */
    public record Row(int num, String fullName, String group, String recordBook, Integer score, Integer maxScore,
                      Integer percent, Integer grade, String gradeWord, LocalDateTime finishedAt) {

        public boolean absent() {
            return grade == null;
        }
    }

    public long count(String key) {
        return totals.getOrDefault(key, 0L);
    }
}
