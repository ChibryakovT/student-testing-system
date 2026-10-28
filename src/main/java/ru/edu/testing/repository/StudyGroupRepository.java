package ru.edu.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.edu.testing.domain.StudyGroup;

import java.util.List;
import java.util.Optional;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {

    Optional<StudyGroup> findByName(String name);

    @Query("select g from StudyGroup g left join fetch g.faculty order by g.course, g.name")
    List<StudyGroup> findAllOrdered();

    /** Группы, в которых преподаватель ведёт хотя бы одну дисциплину. */
    @Query("select distinct c.group from CurriculumItem c where c.teacher.id = :teacherId order by c.group.course, c.group.name")
    List<StudyGroup> findTaughtBy(@Param("teacherId") Long teacherId);

    @Query("select count(u) from User u where u.group.id = :groupId and u.role = ru.edu.testing.domain.Role.STUDENT")
    long countStudents(@Param("groupId") Long groupId);
}
