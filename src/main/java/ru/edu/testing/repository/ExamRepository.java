package ru.edu.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.edu.testing.domain.Exam;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    @Query("""
            select e from Exam e join fetch e.plan c join fetch c.group g join fetch c.subject
            left join fetch c.teacher where g.id = :groupId order by e.date, e.startTime
            """)
    List<Exam> findByGroup(@Param("groupId") Long groupId);

    @Query("""
            select e from Exam e join fetch e.plan c join fetch c.group join fetch c.subject
            left join fetch c.teacher t where t.id = :teacherId order by e.date, e.startTime
            """)
    List<Exam> findByTeacher(@Param("teacherId") Long teacherId);

    Optional<Exam> findByPlanId(Long planId);
}
