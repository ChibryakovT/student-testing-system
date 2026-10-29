package ru.edu.testing.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.QuestionType;
import ru.edu.testing.domain.Test;
import ru.edu.testing.dto.OptionForm;
import ru.edu.testing.dto.QuestionForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.security.AppUserDetails;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Импорт вопросов из Excel. Формат строки: A — текст вопроса, B — баллы, C…H — варианты ответа;
 * правильные варианты отмечаются «+» в конце. Несколько правильных — вопрос с несколькими ответами.
 * Импорт выполняется целиком или не выполняется вовсе: при ошибке в любой строке ничего не сохраняется.
 */
@Service
public class QuestionImportService {

    public static final int MAX_ROWS = 200;

    private final QuestionService questionService;
    private final TestService testService;
    private final AuditService auditService;

    public QuestionImportService(QuestionService questionService, TestService testService, AuditService auditService) {
        this.questionService = questionService;
        this.testService = testService;
        this.auditService = auditService;
    }

    @Transactional
    public int importXlsx(Long testId, InputStream in, AppUserDetails user) {
        Test test = testService.getForEdit(testId, user);
        List<ParsedRow> rows = parse(in);
        if (rows.isEmpty()) {
            throw new BusinessException("В файле нет вопросов. Заполните строки, начиная со второй");
        }
        for (ParsedRow row : rows) {
            try {
                questionService.add(testId, row.form(), user);
            } catch (BusinessException e) {
                throw new BusinessException("Строка " + row.rowNumber() + ": " + e.getMessage() + ". Ничего не импортировано");
            }
        }
        List<QuestionForm> forms = rows.stream().map(ParsedRow::form).toList();
        auditService.log("Вопросы импортированы", "Тест «" + test.getTitle() + "»: " + forms.size() + " шт. из Excel");
        return forms.size();
    }

    /** Вопрос из файла и номер его строки в Excel (для понятных сообщений об ошибках). */
    record ParsedRow(int rowNumber, QuestionForm form) {
    }

    List<ParsedRow> parse(InputStream in) {
        List<ParsedRow> forms = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                String text = row == null ? "" : fmt.formatCellValue(row.getCell(0)).trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (forms.size() >= MAX_ROWS) {
                    throw new BusinessException("Слишком много вопросов в файле: не более " + MAX_ROWS);
                }
                QuestionForm form = new QuestionForm();
                form.setText(text.length() > 1000 ? text.substring(0, 1000) : text);
                String points = fmt.formatCellValue(row.getCell(1)).trim();
                try {
                    form.setPoints(points.isEmpty() ? 1 : Integer.parseInt(points));
                } catch (NumberFormatException e) {
                    throw new BusinessException("Строка " + (r + 1) + ": баллы должны быть целым числом");
                }
                if (form.getPoints() < 1 || form.getPoints() > 100) {
                    throw new BusinessException("Строка " + (r + 1) + ": баллы — от 1 до 100");
                }
                int correct = 0;
                for (int c = 2; c < 2 + QuestionForm.MAX_OPTIONS; c++) {
                    String option = fmt.formatCellValue(row.getCell(c)).trim();
                    if (option.isEmpty()) {
                        continue;
                    }
                    boolean isCorrect = option.endsWith("+");
                    if (isCorrect) {
                        option = option.substring(0, option.length() - 1).trim();
                        correct++;
                    }
                    form.getOptions().add(new OptionForm(option.length() > 500 ? option.substring(0, 500) : option, isCorrect));
                }
                form.setType(correct > 1 ? QuestionType.MULTIPLE : QuestionType.SINGLE);
                forms.add(new ParsedRow(r + 1, form));
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException("Не удалось прочитать файл. Загрузите таблицу Excel (.xlsx) по шаблону");
        }
        return forms;
    }

    /** Шаблон для заполнения с двумя примерами. */
    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Вопросы");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            String[] headers = {"Вопрос", "Баллы", "Вариант 1", "Вариант 2", "Вариант 3", "Вариант 4", "Вариант 5", "Вариант 6"};
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = h.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(head);
            }
            String[][] examples = {
                    {"Какой оператор выбирает данные в SQL?", "1", "SELECT+", "INSERT", "UPDATE", "DELETE"},
                    {"Какие функции являются агрегатными?", "2", "COUNT+", "SUM+", "AVG+", "UPPER"}};
            for (int r = 0; r < examples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < examples[r].length; c++) {
                    if (c == 1) {
                        row.createCell(c).setCellValue(Integer.parseInt(examples[r][c]));
                    } else {
                        row.createCell(c).setCellValue(examples[r][c]);
                    }
                }
            }
            // Подсказка — на отдельном листе: импорт читает только первый лист
            Sheet help = wb.createSheet("Инструкция");
            String[] lines = {
                    "Как заполнить",
                    "1. Каждая строка листа «Вопросы» — один вопрос. Первая строка — заголовок, её не меняйте.",
                    "2. Столбец A — текст вопроса, B — баллы (от 1 до 100), C–H — до шести вариантов ответа.",
                    "3. Правильный вариант отметьте знаком «+» в конце: SELECT+",
                    "4. Несколько «+» — вопрос с несколькими правильными ответами.",
                    "5. Строки-примеры замените своими вопросами и загрузите файл на странице теста."};
            for (int i = 0; i < lines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            help.setColumnWidth(0, 100 * 256);
            sheet.setColumnWidth(0, 60 * 256);
            sheet.setColumnWidth(1, 8 * 256);
            for (int c = 2; c < headers.length; c++) {
                sheet.setColumnWidth(c, 22 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать шаблон", e);
        }
    }
}
