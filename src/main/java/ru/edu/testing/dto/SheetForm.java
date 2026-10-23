package ru.edu.testing.dto;

import java.util.ArrayList;
import java.util.List;

/** Настройки ведомости: что выводить, шапка документа, шкала оценок и параметры файла. */
public class SheetForm {

    // Данные
    private Long testId;
    private String group;
    private String attempt = "best";          // best | last | first
    private boolean includeAbsent = true;     // показывать не проходивших («н/я»)
    private String sort = "name";             // name | grade | book

    // Шкала оценок, % (не ниже)
    private int grade5 = 85;
    private int grade4 = 70;
    private int grade3 = 50;

    // Шапка документа
    private String university;
    private String title = "Зачётно-экзаменационная ведомость";
    private String academicYear;
    private String semester;
    private String faculty;
    private String department;
    private String discipline;
    private String teacher;
    private String controlForm;               // по умолчанию — из дисциплины теста
    private String date;

    // Колонки и параметры файла
    private List<SheetColumn> columns = new ArrayList<>(List.of(
            SheetColumn.NUM, SheetColumn.NAME, SheetColumn.BOOK, SheetColumn.GRADE,
            SheetColumn.GRADE_WORD, SheetColumn.SIGN));
    private String delimiter = "semicolon";   // semicolon | comma | tab
    private String encoding = "UTF-8";        // UTF-8 | windows-1251
    private boolean csvHeader = true;         // шапка документа в CSV

    public char delimiterChar() {
        return switch (delimiter == null ? "" : delimiter) {
            case "comma" -> ',';
            case "tab" -> '\t';
            default -> ';';
        };
    }

    public Long getTestId() { return testId; }
    public void setTestId(Long testId) { this.testId = testId; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getAttempt() { return attempt; }
    public void setAttempt(String attempt) { this.attempt = attempt; }
    public boolean isIncludeAbsent() { return includeAbsent; }
    public void setIncludeAbsent(boolean includeAbsent) { this.includeAbsent = includeAbsent; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    public int getGrade5() { return grade5; }
    public void setGrade5(int grade5) { this.grade5 = grade5; }
    public int getGrade4() { return grade4; }
    public void setGrade4(int grade4) { this.grade4 = grade4; }
    public int getGrade3() { return grade3; }
    public void setGrade3(int grade3) { this.grade3 = grade3; }
    public String getUniversity() { return university; }
    public void setUniversity(String university) { this.university = university; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public String getFaculty() { return faculty; }
    public void setFaculty(String faculty) { this.faculty = faculty; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getDiscipline() { return discipline; }
    public void setDiscipline(String discipline) { this.discipline = discipline; }
    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }
    public String getControlForm() { return controlForm; }
    public void setControlForm(String controlForm) { this.controlForm = controlForm; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public List<SheetColumn> getColumns() { return columns; }
    public void setColumns(List<SheetColumn> columns) { this.columns = columns; }
    public String getDelimiter() { return delimiter; }
    public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }
    public boolean isCsvHeader() { return csvHeader; }
    public void setCsvHeader(boolean csvHeader) { this.csvHeader = csvHeader; }
}
