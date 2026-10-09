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
}
