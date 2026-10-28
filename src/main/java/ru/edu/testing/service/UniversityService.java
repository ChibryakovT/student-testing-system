package ru.edu.testing.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.*;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.repository.*;

import java.util.List;

/** Структура вуза и учебный план: факультеты, кафедры, группы, дисциплины групп и их преподаватели. */
@Service
@Transactional(readOnly = true)
public class UniversityService {

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final StudyGroupRepository groupRepository;
    private final CurriculumRepository curriculumRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;

    public UniversityService(FacultyRepository facultyRepository, DepartmentRepository departmentRepository,
                             StudyGroupRepository groupRepository, CurriculumRepository curriculumRepository,
                             SubjectRepository subjectRepository, UserRepository userRepository) {
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.groupRepository = groupRepository;
        this.curriculumRepository = curriculumRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
    }

    public List<Faculty> faculties() {
        return facultyRepository.findAllByOrderByNameAsc();
    }

    public List<Department> departments() {
        return departmentRepository.findAllWithFaculty();
    }

    public List<StudyGroup> groups() {
        return groupRepository.findAllOrdered();
    }

    public StudyGroup group(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> new NotFoundException("Группа не найдена"));
    }

    public long studentCount(Long groupId) {
        return groupRepository.countStudents(groupId);
    }

    public List<User> teachers() {
        return userRepository.findByRoleOrdered(Role.TEACHER);
    }

    public List<Subject> subjects() {
        return subjectRepository.findAllWithDepartment();
    }

    /** Учебный план группы. */
    public List<CurriculumItem> curriculum(Long groupId) {
        return curriculumRepository.findByGroup(groupId);
    }

    /** Нагрузка преподавателя: какие дисциплины в каких группах он ведёт. */
    public List<CurriculumItem> teacherLoad(Long teacherId) {
        return curriculumRepository.findByTeacher(teacherId);
    }

    public List<StudyGroup> groupsOfTeacher(Long teacherId) {
        return groupRepository.findTaughtBy(teacherId);
    }

    public CurriculumItem planItem(Long groupId, Long subjectId) {
        return curriculumRepository.find(groupId, subjectId)
                .orElseThrow(() -> new BusinessException("У группы нет этой дисциплины в учебном плане"));
    }

    @Transactional
    public StudyGroup createGroup(String name, Integer course, String specialty, Long facultyId) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Укажите название группы");
        }
        if (groupRepository.findByName(name.trim()).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Группа «" + name.trim() + "» уже есть");
        }
        if (course != null && (course < 1 || course > 6)) {
            throw new BusinessException("Курс — от 1 до 6");
        }
        Faculty faculty = facultyId == null ? null : facultyRepository.findById(facultyId)
                .orElseThrow(() -> new BusinessException("Факультет не найден"));
        return groupRepository.save(new StudyGroup(name.trim(), course, blankToNull(specialty), faculty));
    }

    /** Добавить дисциплину в учебный план группы (или обновить преподавателя, семестр и форму контроля). */
    @Transactional
    public CurriculumItem savePlanItem(Long groupId, Long subjectId, Long teacherId, Integer semester,
                                       ControlForm controlForm) {
        StudyGroup group = group(groupId);
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new BusinessException("Дисциплина не найдена"));
        User teacher = null;
        if (teacherId != null) {
            teacher = userRepository.findById(teacherId).orElseThrow(() -> new BusinessException("Преподаватель не найден"));
            if (teacher.getRole() == Role.STUDENT) {
                throw new BusinessException("Вести дисциплину может только преподаватель");
            }
        }
        if (semester != null && (semester < 1 || semester > 12)) {
            throw new BusinessException("Семестр — от 1 до 12");
        }
        CurriculumItem item = curriculumRepository.find(groupId, subjectId)
                .orElseGet(() -> new CurriculumItem(group, subject, null, null, ControlForm.EXAM));
        item.setTeacher(teacher);
        item.setSemester(semester);
        item.setControlForm(controlForm == null ? ControlForm.EXAM : controlForm);
        try {
            return curriculumRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "Эта дисциплина уже есть в плане группы");
        }
    }

    @Transactional
    public Long deletePlanItem(Long itemId) {
        CurriculumItem item = curriculumRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Строка учебного плана не найдена"));
        Long groupId = item.getGroup().getId();
        curriculumRepository.delete(item);
        return groupId;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
