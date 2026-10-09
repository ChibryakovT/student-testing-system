package ru.edu.testing.security;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.edu.testing.repository.UserRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    public AppUserDetailsService(UserRepository userRepository, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        if (loginAttemptService.isBlocked(username)) {
            // Блокировка не зависит от существования логина: по ответу нельзя узнать, есть ли такой пользователь
            throw new LockedException("Вход временно заблокирован");
        }
        return userRepository.findByUsernameIgnoreCase(username.trim())
                .map(u -> new AppUserDetails(u, false))
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }
}
