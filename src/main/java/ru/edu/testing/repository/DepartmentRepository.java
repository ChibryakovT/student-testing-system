package ru.edu.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.edu.testing.domain.Department;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByName(String name);

    @Query("select d from Department d join fetch d.faculty f order by f.name, d.name")
    List<Department> findAllWithFaculty();
}
