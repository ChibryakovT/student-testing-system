package ru.edu.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.edu.testing.domain.Subject;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findAllByOrderByNameAsc();

    @org.springframework.data.jpa.repository.Query("select s from Subject s left join fetch s.department order by s.name")
    List<Subject> findAllWithDepartment();

    List<Subject> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    boolean existsByNameIgnoreCase(String name);
}
