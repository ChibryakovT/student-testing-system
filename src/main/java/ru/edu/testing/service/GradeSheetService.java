package ru.edu.testing.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.Role;
import ru.edu.testing.domain.Test;
import ru.edu.testing.domain.TestAttempt;
import ru.edu.testing.domain.User;
import ru.edu.testing.dto.GradeSheet;
import ru.edu.testing.dto.GradeSheet.Row;
import ru.edu.testing.dto.SheetColumn;
import ru.edu.testing.dto.SheetForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.repository.TestAttemptRepository;
import ru.edu.testing.repository.TestRepository;
import ru.edu.testing.repository.UserRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BinaryOperator;

/**
 * Ведомость по тесту в формате, принятом в вузе: шапка документа, список студентов группы,
 * оценка по пятибалльной шкале, итоги по оценкам и строки для подписей.
 */
@Service
@Transactional(readOnly = true)
public class GradeSheetService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<String> TOTAL_KEYS = List.of("5", "4", "3", "2", "н/я");

    private final TestRepository testRepository;
    private final TestAttemptRepository attemptRepository;
    private final UserRepository userRepository;

    public GradeSheetService(TestRepository testRepository, TestAttemptRepository attemptRepository,
                             UserRepository userRepository) {
        this.testRepository = testRepository;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
    }

    /** Заполняет пустые поля шапки значениями по умолчанию (дисциплина, преподаватель, год, дата). */
    public void fillDefaults(SheetForm form, String university) {
        if (isBlank(form.getUniversity())) {
            form.setUniversity(university);
        }
        LocalDate today = LocalDate.now();
        if (isBlank(form.getAcademicYear())) {
            int start = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
            form.setAcademicYear(start + "-" + (start + 1));
        }
        if (isBlank(form.getSemester())) {
            form.setSemester(today.getMonthValue() >= 9 || today.getMonthValue() == 1 ? "первый" : "второй");
        }
        if (isBlank(form.getDate())) {
            form.setDate(today.format(DATE));
        }
        if (form.getTestId() != null) {
            testRepository.findById(form.getTestId()).ifPresent(t -> {
                if (isBlank(form.getDiscipline())) {
                    form.setDiscipline(t.getSubject().getName());
                }
                if (isBlank(form.getTeacher())) {
                    form.setTeacher(t.getAuthor().getFullName());
                }
            });
        }
    }

    public GradeSheet build(SheetForm form) {
        if (form.getTestId() == null) {
            throw new BusinessException("Выберите тест для ведомости");
        }
        if (!(form.getGrade5() > form.getGrade4() && form.getGrade4() > form.getGrade3()
                && form.getGrade3() >= 0 && form.getGrade5() <= 100)) {
            throw new BusinessException("Шкала оценок: должно быть 100 ≥ «5» > «4» > «3» ≥ 0");
        }
        Test test = testRepository.findById(form.getTestId()).orElseThrow(() -> new NotFoundException("Тест не найден"));
        String group = isBlank(form.getGroup()) ? null : form.getGroup().trim();

        // Одна попытка на студента: лучшая, последняя или первая
        List<TestAttempt> attempts = attemptRepository.search(null, test.getId(), null, null, group, "%",
                Sort.by("finishedAt"));
        BinaryOperator<TestAttempt> pick = switch (form.getAttempt()) {
            case "last" -> (a, b) -> b;
            case "first" -> (a, b) -> a;
            default -> (a, b) -> b.getPercent() > a.getPercent() ? b : a;
        };
        Map<Long, TestAttempt> byStudent = new LinkedHashMap<>();
        attempts.forEach(a -> byStudent.merge(a.getStudent().getId(), a, pick));

        // Список студентов: все студенты группы (для отметки «н/я») или только проходившие
        Map<Long, User> students = new LinkedHashMap<>();
        if (form.isIncludeAbsent()) {
            userRepository.search("%", Role.STUDENT, group, Sort.by("fullName"))
                    .stream().filter(User::isEnabled).forEach(u -> students.put(u.getId(), u));
        }
        byStudent.values().forEach(a -> students.putIfAbsent(a.getStudent().getId(), a.getStudent()));

        List<Row> rows = new ArrayList<>();
        for (User u : students.values()) {
            TestAttempt a = byStudent.get(u.getId());
            Integer grade = a == null ? null : grade(a.getPercent(), form);
            rows.add(new Row(0, u.getFullName(), u.getGroupName(), u.getRecordBook(),
                    a == null ? null : a.getScore(), a == null ? null : a.getMaxScore(),
                    a == null ? null : a.getPercent(), grade, gradeWord(grade), a == null ? null : a.getFinishedAt()));
        }
        Comparator<Row> byName = Comparator.comparing(Row::fullName, String.CASE_INSENSITIVE_ORDER);
        rows.sort(switch (form.getSort()) {
            case "grade" -> Comparator.comparing((Row r) -> r.grade() == null ? -1 : r.grade()).reversed().thenComparing(byName);
            case "book" -> Comparator.comparing((Row r) -> r.recordBook() == null ? "" : r.recordBook()).thenComparing(byName);
            default -> byName;
        });
        List<Row> numbered = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            numbered.add(new Row(i + 1, r.fullName(), r.group(), r.recordBook(), r.score(), r.maxScore(), r.percent(),
                    r.grade(), r.gradeWord(), r.finishedAt()));
        }

        Map<String, Long> totals = new LinkedHashMap<>();
        TOTAL_KEYS.forEach(k -> totals.put(k, 0L));
        numbered.forEach(r -> totals.merge(r.absent() ? "н/я" : String.valueOf(r.grade()), 1L, Long::sum));

        List<SheetColumn> columns = form.getColumns() == null || form.getColumns().isEmpty()
                ? List.of(SheetColumn.NUM, SheetColumn.NAME) : form.getColumns();
        return new GradeSheet(form, test.getTitle(), columns, numbered, totals);
    }

    /** Перевод процента в пятибалльную шкалу по заданным порогам. */
    static int grade(int percent, SheetForm form) {
        if (percent >= form.getGrade5()) return 5;
        if (percent >= form.getGrade4()) return 4;
        if (percent >= form.getGrade3()) return 3;
        return 2;
    }

    static String gradeWord(Integer grade) {
        if (grade == null) return "не явился";
        return switch (grade) {
            case 5 -> "отлично";
            case 4 -> "хорошо";
            case 3 -> "удовлетворительно";
            default -> "неудовлетворительно";
        };
    }

    // Методы для шаблонов Thymeleaf (вызов через ${@gradeSheetService...})
    public String value(Row r, SheetColumn c) { return cell(r, c); }
    public List<String> header(GradeSheet s) { return headerLines(s); }
    public String totals(GradeSheet s) { return totalsLine(s); }

    /** Значение ячейки для выбранной колонки. */
    public static String cell(Row r, SheetColumn c) {
        return switch (c) {
            case NUM -> String.valueOf(r.num());
            case NAME -> r.fullName();
            case GROUP -> nz(r.group());
            case BOOK -> nz(r.recordBook());
            case SCORE -> r.absent() ? "" : r.score() + " / " + r.maxScore();
            case PERCENT -> r.absent() ? "" : String.valueOf(r.percent());
            case GRADE -> r.absent() ? "н/я" : String.valueOf(r.grade());
            case GRADE_WORD -> r.gradeWord();
            case DATE -> r.finishedAt() == null ? "" : r.finishedAt().toLocalDate().format(DATE);
            case SIGN -> "";
        };
    }

    /** Строки шапки документа (пустые поля пропускаются). */
    public static List<String> headerLines(GradeSheet sheet) {
        SheetForm f = sheet.form();
        List<String> lines = new ArrayList<>();
        add(lines, "", f.getUniversity());
        add(lines, "", f.getTitle());
        add(lines, "Учебный год: ", join(f.getAcademicYear(), isBlank(f.getSemester()) ? null : "семестр: " + f.getSemester()));
        add(lines, "Факультет: ", f.getFaculty());
        add(lines, "Кафедра: ", f.getDepartment());
        add(lines, "Группа: ", isBlank(f.getGroup()) ? "все группы" : f.getGroup());
        add(lines, "Дисциплина: ", f.getDiscipline());
        add(lines, "Тест: ", sheet.testTitle());
        add(lines, "Форма контроля: ", f.getControlForm());
        add(lines, "Преподаватель: ", f.getTeacher());
        add(lines, "Дата: ", f.getDate());
        return lines;
    }

    public static String totalsLine(GradeSheet s) {
        return "Итого: 5 (отл.) = " + s.count("5") + ", 4 (хор.) = " + s.count("4") + ", 3 (удовл.) = " + s.count("3")
                + ", 2 (неуд.) = " + s.count("2") + ", не явились = " + s.count("н/я") + ", всего = " + s.rows().size();
    }

    // ---------- CSV ----------

    public byte[] toCsv(GradeSheet sheet) {
        SheetForm f = sheet.form();
        char d = f.delimiterChar();
        StringBuilder sb = new StringBuilder();
        if (f.isCsvHeader()) {
            headerLines(sheet).forEach(l -> sb.append(CsvUtil.escape(l, d)).append("\r\n"));
            sb.append("\r\n");
        }
        sb.append(String.join(String.valueOf(d), sheet.columns().stream().map(c -> CsvUtil.escape(c.getTitle(), d)).toList())).append("\r\n");
        for (Row r : sheet.rows()) {
            sb.append(String.join(String.valueOf(d), sheet.columns().stream().map(c -> CsvUtil.escape(cell(r, c), d)).toList())).append("\r\n");
        }
        if (f.isCsvHeader()) {
            sb.append("\r\n").append(CsvUtil.escape(totalsLine(sheet), d)).append("\r\n");
            sb.append(CsvUtil.escape("Преподаватель: ____________________ " + nz(f.getTeacher()), d)).append("\r\n");
            sb.append(CsvUtil.escape("Декан факультета: ____________________", d)).append("\r\n");
        }
        boolean utf8 = !"windows-1251".equalsIgnoreCase(f.getEncoding());
        byte[] body = sb.toString().getBytes(utf8 ? StandardCharsets.UTF_8 : Charset.forName("windows-1251"));
        if (!utf8) {
            return body;
        }
        // BOM нужен, чтобы Excel распознал кириллицу в UTF-8
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF; withBom[1] = (byte) 0xBB; withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return withBom;
    }

    // ---------- Excel ----------

    public byte[] toXlsx(GradeSheet sheet) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet ws = wb.createSheet("Ведомость");
            int cols = sheet.columns().size();

            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle title = wb.createCellStyle();
            title.setFont(bold);
            title.setAlignment(HorizontalAlignment.CENTER);
            CellStyle plain = wb.createCellStyle();
            CellStyle head = bordered(wb);
            head.setFont(bold);
            head.setAlignment(HorizontalAlignment.CENTER);
            head.setVerticalAlignment(VerticalAlignment.CENTER);
            head.setWrapText(true);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle cell = bordered(wb);
            CellStyle center = bordered(wb);
            center.setAlignment(HorizontalAlignment.CENTER);

            int r = 0;
            List<String> header = headerLines(sheet);
            for (int i = 0; i < header.size(); i++) {
                Row0 row = new Row0(ws.createRow(r));
                row.set(0, header.get(i), i < 2 ? title : plain);
                if (i < 2 && cols > 1) {
                    ws.addMergedRegion(new CellRangeAddress(r, r, 0, cols - 1));
                }
                r++;
            }
            r++;
            Row0 hr = new Row0(ws.createRow(r++));
            for (int c = 0; c < cols; c++) {
                hr.set(c, sheet.columns().get(c).getTitle(), head);
            }
            for (Row row : sheet.rows()) {
                Row0 xr = new Row0(ws.createRow(r++));
                for (int c = 0; c < cols; c++) {
                    SheetColumn col = sheet.columns().get(c);
                    String v = cell(row, col);
                    boolean numeric = (col == SheetColumn.NUM || col == SheetColumn.PERCENT
                            || (col == SheetColumn.GRADE && !row.absent())) && !v.isEmpty();
                    if (numeric) {
                        xr.setNumber(c, Double.parseDouble(v), center);
                    } else {
                        xr.set(c, v, col == SheetColumn.NAME ? cell : center);
                    }
                }
            }
            r++;
            new Row0(ws.createRow(r++)).set(0, totalsLine(sheet), plain);
            r++;
            new Row0(ws.createRow(r++)).set(0, "Преподаватель: ____________________ " + nz(sheet.form().getTeacher()), plain);
            new Row0(ws.createRow(r + 1)).set(0, "Декан факультета: ____________________", plain);

            for (int c = 0; c < cols; c++) {
                SheetColumn col = sheet.columns().get(c);
                int width = switch (col) {
                    case NUM -> 5;
                    case NAME -> 38;
                    case GRADE_WORD -> 20;
                    case SIGN -> 16;
                    case BOOK, DATE, GROUP -> 14;
                    default -> 10;
                };
                ws.setColumnWidth(c, width * 256);
            }
            PrintSetup ps = ws.getPrintSetup();
            ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
            ws.setFitToPage(true);
            ps.setFitWidth((short) 1);
            ps.setFitHeight((short) 0);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать Excel-файл", e);
        }
    }

    private static CellStyle bordered(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    /** Обёртка над строкой Excel для краткости записи ячеек. */
    private record Row0(org.apache.poi.ss.usermodel.Row row) {
        void set(int c, String v, CellStyle style) {
            Cell cell = row.createCell(c);
            cell.setCellValue(v);
            cell.setCellStyle(style);
        }

        void setNumber(int c, double v, CellStyle style) {
            Cell cell = row.createCell(c);
            cell.setCellValue(v);
            cell.setCellStyle(style);
        }
    }

    /** Имя файла: Ведомость_<тест>_<группа>. */
    public static String fileName(GradeSheet sheet, String ext) {
        String base = "Ведомость_" + sheet.testTitle() + (isBlank(sheet.form().getGroup()) ? "" : "_" + sheet.form().getGroup());
        return base.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").replaceAll("_+", "_") + "." + ext;
    }

    private static void add(List<String> lines, String label, String value) {
        if (!isBlank(value)) {
            lines.add(label + value.trim());
        }
    }

    private static String join(String a, String b) {
        if (isBlank(a)) return b;
        if (isBlank(b)) return a;
        return a + ", " + b;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
