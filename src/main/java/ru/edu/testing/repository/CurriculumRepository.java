package ru.edu.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.edu.testing.domain.CurriculumItem;
import ru.edu.testing.domain.Subject;

import java.util.List;
import java.util.Optional;

/** Учебный план: какая дисциплина у какой группы и кто её ведёт. */
public interface CurriculumRepository extends JpaRepository<CurriculumItem, Long> {

    @Query("""
            select c from CurriculumItem c join fetch c.subject s left join fetch s.department
            left join fetch c.teacher where c.group.id = :groupId order by s.name
            """)
    List<CurriculumItem> findByGroup(@Param("groupId") Long groupId);

    @Query("""
            select c from CurriculumItem c join fetch c.group g join fetch c.subject s left join fetch c.teacher
            where c.teacher.id = :teacherId order by g.course, g.name, s.name
            """)
    List<CurriculumItem> findByTeacher(@Param("teacherId") Long teacherId);

    @Query("""
            select c from CurriculumItem c join fetch c.group g left join fetch g.faculty join fetch c.subject s
            left join fetch s.department d left join fetch c.teacher
            where g.id = :groupId and s.id = :subjectId
            """)
    Optional<CurriculumItem> find(@Param("groupId") Long groupId, @Param("subjectId") Long subjectId);

    /** Дисциплины, которые ведёт преподаватель (в любой группе). */
    @Query("select distinct c.subject from CurriculumItem c where c.teacher.id = :teacherId order by c.subject.name")
    List<Subject> findSubjectsTaughtBy(@Param("teacherId") Long teacherId);

    /** Весь учебный план с группами и преподавателями — для справочника дисциплин. */
    @Query("""
            select c from CurriculumItem c join fetch c.group g join fetch c.subject s left join fetch c.teacher
            order by s.name, g.course, g.name
            """)
    List<CurriculumItem> findAllWithDetails();

    @Query("""
            select c from CurriculumItem c join fetch c.group g left join fetch c.teacher
            where c.subject.id = :subjectId order by g.course, g.name
            """)
    List<CurriculumItem> findBySubject(@Param("subjectId") Long subjectId);

    boolean existsByTeacherIdAndSubjectId(Long teacherId, Long subjectId);

    boolean existsByTeacherIdAndGroupIdAndSubjectId(Long teacherId, Long groupId, Long subjectId);

    boolean existsByGroupIdAndSubjectId(Long groupId, Long subjectId);
}
