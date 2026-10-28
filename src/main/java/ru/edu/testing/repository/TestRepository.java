package ru.edu.testing.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.edu.testing.domain.Test;

import java.util.List;

public interface TestRepository extends JpaRepository<Test, Long> {

    /** Все тесты (администратор). */
    @Query("""
            select t from Test t join fetch t.subject s join fetch t.author a
            where lower(t.title) like :pattern and (:subjectId is null or s.id = :subjectId)
            """)
    List<Test> search(@Param("pattern") String pattern, @Param("subjectId") Long subjectId, Sort sort);

    /** Студент: только опубликованные тесты по дисциплинам учебного плана его группы. */
    @Query("""
            select t from Test t join fetch t.subject s join fetch t.author a
            where lower(t.title) like :pattern and (:subjectId is null or s.id = :subjectId)
              and t.published = true
              and exists (select 1 from CurriculumItem c, User st
                          where st.id = :studentId and c.group = st.group and c.subject = s)
            """)
    List<Test> searchForStudent(@Param("pattern") String pattern, @Param("subjectId") Long subjectId,
                                @Param("studentId") Long studentId, Sort sort);

    /** Преподаватель: свои тесты и тесты по дисциплинам, которые он ведёт. */
    @Query("""
            select t from Test t join fetch t.subject s join fetch t.author a
            where lower(t.title) like :pattern and (:subjectId is null or s.id = :subjectId)
              and (a.id = :teacherId
                   or exists (select 1 from CurriculumItem c where c.teacher.id = :teacherId and c.subject = s))
            """)
    List<Test> searchForTeacher(@Param("pattern") String pattern, @Param("subjectId") Long subjectId,
                                @Param("teacherId") Long teacherId, Sort sort);

    @Query("select t from Test t join fetch t.subject s join fetch t.author where s.id = :subjectId order by t.title")
    List<Test> findBySubject(@Param("subjectId") Long subjectId);

    long countByPublishedTrue();

    boolean existsBySubjectId(Long subjectId);
}
