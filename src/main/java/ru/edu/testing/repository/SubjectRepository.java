package ru.edu.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.edu.testing.domain.Subject;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findAllByOrderByNameAsc();

    List<Subject> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    boolean existsByNameIgnoreCase(String name);
}
