package ru.edu.testing.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.edu.testing.domain.AttemptStatus;
import ru.edu.testing.domain.TestAttempt;
import ru.edu.testing.dto.QuestionStatRow;
import ru.edu.testing.dto.TestStatRow;

import java.util.List;
import java.util.Optional;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, Long> {

    @Query("""
            select a from TestAttempt a join fetch a.test t join fetch t.subject s join fetch a.student u
            left join fetch u.group g
            where a.status = ru.edu.testing.domain.AttemptStatus.FINISHED
              and (:studentId is null or u.id = :studentId)
              and (:testId is null or t.id = :testId)
              and (:subjectId is null or s.id = :subjectId)
              and (:passed is null or a.passed = :passed)
              and (:groupId is null or g.id = :groupId)
              and (:teacherId is null or t.author.id = :teacherId
                   or exists (select 1 from CurriculumItem c
                              where c.teacher.id = :teacherId and c.group = g and c.subject = s))
              and (lower(u.fullName) like :pattern or lower(t.title) like :pattern
                   or lower(coalesce(g.name, '')) like :pattern)
            """)
    List<TestAttempt> search(@Param("studentId") Long studentId,
                             @Param("teacherId") Long teacherId,
                             @Param("testId") Long testId,
                             @Param("subjectId") Long subjectId,
                             @Param("passed") Boolean passed,
                             @Param("groupId") Long groupId,
                             @Param("pattern") String pattern,
                             Sort sort);

    Optional<TestAttempt> findFirstByTestIdAndStudentIdAndStatus(Long testId, Long studentId, AttemptStatus status);

    long countByStatus(AttemptStatus status);

    @Query("""
            select new ru.edu.testing.dto.GroupStatRow(g.id, g.name, count(a.id), coalesce(avg(a.percent), 0),
                sum(case when a.passed = true then 1 else 0 end))
            from TestAttempt a join a.student u join u.group g join a.test t
            where a.status = ru.edu.testing.domain.AttemptStatus.FINISHED
              and (:subjectId is null or t.subject.id = :subjectId)
            group by g.id, g.name
            order by avg(a.percent) desc
            """)
    List<ru.edu.testing.dto.GroupStatRow> groupStatistics(@Param("subjectId") Long subjectId);

    @Query("""
            select new ru.edu.testing.dto.StudentRankRow(u.id, u.fullName, g.name, count(a.id), avg(a.percent), max(a.percent))
            from TestAttempt a join a.student u join u.group g join a.test t
            where a.status = ru.edu.testing.domain.AttemptStatus.FINISHED
              and (:subjectId is null or t.subject.id = :subjectId)
              and (:groupId is null or g.id = :groupId)
            group by u.id, u.fullName, g.name
            having count(a.id) >= :minAttempts
            order by avg(a.percent) desc, count(a.id) desc, u.fullName
            """)
    List<ru.edu.testing.dto.StudentRankRow> ranking(@Param("subjectId") Long subjectId, @Param("groupId") Long groupId,
                                                    @Param("minAttempts") long minAttempts,
                                                    org.springframework.data.domain.Pageable page);

    long countByTestIdAndStudentIdAndStatus(Long testId, Long studentId, AttemptStatus status);

    Optional<TestAttempt> findFirstByTestIdAndStudentIdAndStatusOrderByFinishedAtDesc(Long testId, Long studentId,
                                                                                    AttemptStatus status);

    List<TestAttempt> findByTestIdAndStatus(Long testId, AttemptStatus status);

    long countByTestIdAndStatusAndReleasedFalse(Long testId, AttemptStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update TestAttempt a set a.released = true where a.test.id = :testId and a.released = false")
    int releaseAll(@Param("testId") Long testId);

    long countByStatusAndPassedTrue(AttemptStatus status);

    @Query("select coalesce(avg(a.percent), 0) from TestAttempt a where a.status = ru.edu.testing.domain.AttemptStatus.FINISHED")
    double averagePercent();

    @Query("""
            select new ru.edu.testing.dto.TestStatRow(
                t.id, t.title, s.name, count(a.id),
                coalesce(avg(a.percent), 0), coalesce(min(a.percent), 0), coalesce(max(a.percent), 0),
                sum(case when a.passed = true then 1 else 0 end))
            from Test t join t.subject s
            left join TestAttempt a on a.test = t and a.status = ru.edu.testing.domain.AttemptStatus.FINISHED
            where (:subjectId is null or s.id = :subjectId)
            group by t.id, t.title, s.name
            order by count(a.id) desc, t.title asc
            """)
    List<TestStatRow> testStatistics(@Param("subjectId") Long subjectId);

    @Query("""
            select new ru.edu.testing.dto.QuestionStatRow(
                q.id, q.text, count(aa.id), sum(case when aa.correct = true then 1 else 0 end))
            from AttemptAnswer aa join aa.question q
            where q.test.id = :testId
            group by q.id, q.text, q.position
            order by q.position asc, q.id asc
            """)
    List<QuestionStatRow> questionStatistics(@Param("testId") Long testId);
}
