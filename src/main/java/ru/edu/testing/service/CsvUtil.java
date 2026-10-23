package ru.edu.testing.service;

/** Экранирование значений для CSV. */
public final class CsvUtil {

    private CsvUtil() {
    }

    /**
     * Значения, начинающиеся с = + - @, получают апостроф, чтобы Excel не выполнил их
     * как формулу (защита от CSV-инъекции). Значения с разделителем, кавычкой или
     * переводом строки заключаются в кавычки.
     */
    public static String escape(String value, char delimiter) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.indexOf(delimiter) >= 0 || v.contains("\"") || v.contains("\n")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
