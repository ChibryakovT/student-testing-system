package ru.edu.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.edu.testing.domain.Question;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    long countByTestId(Long testId);
}
