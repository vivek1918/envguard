package com.envguard.support;

import com.envguard.domain.entity.Role;
import com.envguard.domain.entity.User;
import com.envguard.domain.enums.RoleName;
import com.envguard.repository.RoleRepository;
import com.envguard.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class TestDataHelper {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataHelper(UserRepository userRepository, RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(String username, String email, String password, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .enabled(true)
                .roles(Set.of(role))
                .build();
        return userRepository.save(user);
    }

    public User createUser(String username, RoleName roleName) {
        return createUser(username, username + "@example.com", "password123", roleName);
    }

    public UUID id(User user) {
        return user.getId();
    }
}
