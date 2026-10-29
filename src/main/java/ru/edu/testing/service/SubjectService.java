package ru.edu.testing.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.Subject;
import ru.edu.testing.dto.SubjectForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.repository.DepartmentRepository;
import ru.edu.testing.repository.SubjectRepository;
import ru.edu.testing.repository.TestRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SubjectService {

    private final AuditService auditService;
    private final SubjectRepository subjectRepository;
    private final TestRepository testRepository;
    private final DepartmentRepository departmentRepository;

    public SubjectService(SubjectRepository subjectRepository, TestRepository testRepository,
                          DepartmentRepository departmentRepository, AuditService auditService) {
        this.auditService = auditService;
        this.subjectRepository = subjectRepository;
        this.testRepository = testRepository;
        this.departmentRepository = departmentRepository;
    }

    public List<Subject> findAll(String query) {
        return findAll(query, null);
    }

    /** Поиск по названию и фильтр по кафедре. */
    public List<Subject> findAll(String query, Long departmentId) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return subjectRepository.findAllWithDepartment().stream()
                .filter(s -> q.isEmpty() || s.getName().toLowerCase().contains(q))
                .filter(s -> departmentId == null || (s.getDepartment() != null && departmentId.equals(s.getDepartment().getId())))
                .toList();
    }

    public Subject get(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> new NotFoundException("Дисциплина не найдена"));
    }

    @Transactional
    public Subject create(SubjectForm form) {
        if (subjectRepository.existsByNameIgnoreCase(form.getName().trim())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Дисциплина с таким названием уже существует");
        }
        Subject subject = new Subject();
        apply(subject, form);
        auditService.log("Дисциплина добавлена", subject.getName());
        return subjectRepository.save(subject);
    }

    @Transactional
    public Subject update(Long id, SubjectForm form) {
        Subject subject = get(id);
        if (!subject.getName().equalsIgnoreCase(form.getName().trim())
                && subjectRepository.existsByNameIgnoreCase(form.getName().trim())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Дисциплина с таким названием уже существует");
        }
        apply(subject, form);
        auditService.log("Дисциплина изменена", subject.getName());
        return subject;
    }

    @Transactional
    public void delete(Long id) {
        Subject subject = get(id);
        if (testRepository.existsBySubjectId(id)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "По дисциплине есть тесты — сначала удалите или перенесите их");
        }
        auditService.log("Дисциплина удалена", subject.getName());
        subjectRepository.delete(subject);
    }

    private void apply(Subject subject, SubjectForm form) {
        subject.setName(form.getName().trim());
        subject.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
        subject.setDepartment(form.getDepartmentId() == null ? null : departmentRepository.findById(form.getDepartmentId())
                .orElseThrow(() -> new BusinessException("Кафедра не найдена")));
    }
}
