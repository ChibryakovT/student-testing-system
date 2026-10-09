package ru.edu.testing.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.edu.testing.domain.Subject;
import ru.edu.testing.dto.SubjectForm;
import ru.edu.testing.exception.BusinessException;
import ru.edu.testing.exception.NotFoundException;
import ru.edu.testing.repository.SubjectRepository;
import ru.edu.testing.repository.TestRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final TestRepository testRepository;

    public SubjectService(SubjectRepository subjectRepository, TestRepository testRepository) {
        this.subjectRepository = subjectRepository;
        this.testRepository = testRepository;
    }

    public List<Subject> findAll(String query) {
        if (query == null || query.isBlank()) {
            return subjectRepository.findAllByOrderByNameAsc();
        }
        return subjectRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
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
        return subject;
    }

    @Transactional
    public void delete(Long id) {
        Subject subject = get(id);
        if (testRepository.existsBySubjectId(id)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "По дисциплине есть тесты — сначала удалите или перенесите их");
        }
        subjectRepository.delete(subject);
    }

    private static void apply(Subject subject, SubjectForm form) {
        subject.setName(form.getName().trim());
        subject.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
    }
}
