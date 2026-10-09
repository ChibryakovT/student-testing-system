package ru.edu.testing.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.edu.testing.domain.Test;

import java.util.List;

public interface TestRepository extends JpaRepository<Test, Long> {

    @Query("""
            select t from Test t join fetch t.subject s join fetch t.author a
            where lower(t.title) like :pattern
              and (:subjectId is null or s.id = :subjectId)
              and (:onlyPublished = false or t.published = true)
            """)
    List<Test> search(@Param("pattern") String pattern,
                      @Param("subjectId") Long subjectId,
                      @Param("onlyPublished") boolean onlyPublished,
                      Sort sort);

    long countByPublishedTrue();

    boolean existsBySubjectId(Long subjectId);
}
