package com.envguard.service;

import com.envguard.domain.entity.Role;
import com.envguard.domain.entity.User;
import com.envguard.domain.enums.RoleName;
import com.envguard.dto.request.LoginRequest;
import com.envguard.dto.request.RegisterRequest;
import com.envguard.exception.ApiException;
import com.envguard.repository.RoleRepository;
import com.envguard.repository.UserRepository;
import com.envguard.security.JwtService;
import com.envguard.security.UserPrincipal;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditService auditService;

    @Data
    @Builder
    public static class AuthResult {
        private String token;
        private UserView user;
    }

    @Data
    @Builder
    public static class UserView {
        private String id;
        private String username;
        private String email;
        private Set<String> roles;
        private boolean enabled;
    }

    /**
     * First registered user becomes ADMINISTRATOR when the user table is empty; otherwise DEVELOPER.
     * Bootstrap admin can also be created via ENVGUARD_BOOTSTRAP_ADMIN_* (AdminBootstrapRunner).
     */
    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw ApiException.conflict("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw ApiException.conflict("Email already registered");
        }

        boolean firstUser = userRepository.count() == 0;
        RoleName roleName = firstUser ? RoleName.ADMINISTRATOR : RoleName.DEVELOPER;
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> ApiException.badRequest("Role not seeded: " + roleName));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .roles(Set.of(role))
                .build();
        userRepository.save(user);

        auditService.record(null, user.getId(), null, "USER_REGISTERED", "User", user.getId().toString(),
                Map.of("username", user.getUsername(), "firstUserAdmin", firstUser, "role", roleName.name()));

        UserPrincipal principal = new UserPrincipal(user);
        return AuthResult.builder()
                .token(jwtService.generateToken(principal))
                .user(toView(user))
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResult login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        User user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> ApiException.unauthorized("Invalid credentials"));
        auditService.record(null, user.getId(), null, "USER_LOGIN", "User", user.getId().toString(), Map.of());
        return AuthResult.builder()
                .token(jwtService.generateToken(principal))
                .user(toView(user))
                .build();
    }

    @Transactional(readOnly = true)
    public UserView me(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return toView(user);
    }

    public static UserView toView(User user) {
        return UserView.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toSet()))
                .enabled(user.isEnabled())
                .build();
    }
}
