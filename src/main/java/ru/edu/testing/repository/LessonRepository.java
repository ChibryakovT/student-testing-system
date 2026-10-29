package ru.edu.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.edu.testing.domain.Lesson;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    @Query("""
            select l from Lesson l join fetch l.group left join fetch l.subject
            left join fetch l.teacher left join fetch l.coTeacher
            where l.group.id = :groupId order by l.dayOfWeek, l.pairNo, l.weekType
            """)
    List<Lesson> findByGroup(@Param("groupId") Long groupId);

    @Query("""
            select l from Lesson l join fetch l.group left join fetch l.subject
            left join fetch l.teacher t left join fetch l.coTeacher c
            where t.id = :teacherId or c.id = :teacherId order by l.dayOfWeek, l.pairNo, l.weekType
            """)
    List<Lesson> findByTeacher(@Param("teacherId") Long teacherId);
}
