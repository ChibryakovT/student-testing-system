package ru.edu.testing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportServiceTest {

    @Test
    @DisplayName("CSV: значения-формулы экранируются (защита от CSV-инъекции)")
    void csvInjectionIsEscaped() {
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", ReportService.csv("=HYPERLINK(\"x\")"));
        assertEquals("'+79991234567", ReportService.csv("+79991234567"));
        assertEquals("\"a;b\"", ReportService.csv("a;b"));
        assertEquals("Обычный текст", ReportService.csv("Обычный текст"));
    }

    @Test
    @DisplayName("Ведомость: перевод процента в оценку по шкале 85/70/50")
    void gradeScale() {
        ru.edu.testing.dto.SheetForm f = new ru.edu.testing.dto.SheetForm();
        assertEquals(5, GradeSheetService.grade(85, f));
        assertEquals(4, GradeSheetService.grade(84, f));
        assertEquals(3, GradeSheetService.grade(50, f));
        assertEquals(2, GradeSheetService.grade(49, f));
        assertEquals("не явился", GradeSheetService.gradeWord(null));
    }
}
