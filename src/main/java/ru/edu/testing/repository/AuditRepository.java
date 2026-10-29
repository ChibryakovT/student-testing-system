package ru.edu.testing.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.edu.testing.domain.AuditEntry;

import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    @Query("""
            select a from AuditEntry a
            where (lower(a.username) like :pattern or lower(coalesce(a.details, '')) like :pattern)
              and (:action is null or a.action = :action)
            order by a.createdAt desc, a.id desc
            """)
    List<AuditEntry> search(@Param("pattern") String pattern, @Param("action") String action, Pageable page);

    @Query("select distinct a.action from AuditEntry a order by a.action")
    List<String> actions();
}
