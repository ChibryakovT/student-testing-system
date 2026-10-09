package ru.edu.testing.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ru.edu.testing.domain.Role;
import ru.edu.testing.domain.User;

import java.util.Collection;
import java.util.List;

/** Текущий пользователь в контексте Spring Security. */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final boolean enabled;
    private final boolean locked;

    public AppUserDetails(User user, boolean locked) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.role = user.getRole();
        this.enabled = user.isEnabled();
        this.locked = locked;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }

    public boolean isStudent() { return role == Role.STUDENT; }
    public boolean isAdmin() { return role == Role.ADMIN; }
    public boolean isStaff() { return role == Role.TEACHER || role == Role.ADMIN; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
