package ru.edu.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.edu.testing.domain.Faculty;

import java.util.List;
import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    Optional<Faculty> findByName(String name);

    List<Faculty> findAllByOrderByNameAsc();
}
