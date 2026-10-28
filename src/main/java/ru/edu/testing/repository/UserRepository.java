package ru.edu.testing.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.edu.testing.domain.Role;
import ru.edu.testing.domain.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    @Query("""
            select u from User u left join fetch u.group g left join fetch u.department d
            where (lower(u.username) like :pattern or lower(u.fullName) like :pattern
                   or lower(coalesce(g.name, '')) like :pattern or lower(coalesce(d.name, '')) like :pattern)
              and (:role is null or u.role = :role)
              and (:groupId is null or g.id = :groupId)
            """)
    List<User> search(@Param("pattern") String pattern, @Param("role") Role role,
                      @Param("groupId") Long groupId, Sort sort);

    /** Активные студенты группы — для ведомости. */
    @Query("""
            select u from User u where u.group.id = :groupId and u.role = ru.edu.testing.domain.Role.STUDENT
              and u.enabled = true order by u.fullName
            """)
    List<User> findStudentsOfGroup(@Param("groupId") Long groupId);

    @Query("select u from User u left join fetch u.department where u.role = :role order by u.fullName")
    List<User> findByRoleOrdered(@Param("role") Role role);
}
