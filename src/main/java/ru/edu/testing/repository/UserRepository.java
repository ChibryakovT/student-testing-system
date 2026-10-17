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
            select u from User u
            where (lower(u.username) like :pattern or lower(u.fullName) like :pattern
                   or lower(coalesce(u.groupName, '')) like :pattern)
              and (:role is null or u.role = :role)
              and (:groupName is null or u.groupName = :groupName)
            """)
    List<User> search(@Param("pattern") String pattern, @Param("role") Role role,
                      @Param("groupName") String groupName, Sort sort);

    @Query("select distinct u.groupName from User u where u.groupName is not null order by u.groupName")
    List<String> findGroupNames();
}
