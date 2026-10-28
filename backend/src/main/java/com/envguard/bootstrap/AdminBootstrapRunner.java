package com.envguard.bootstrap;

import com.envguard.config.EnvGuardProperties;
import com.envguard.domain.entity.Role;
import com.envguard.domain.entity.User;
import com.envguard.domain.enums.RoleName;
import com.envguard.repository.RoleRepository;
import com.envguard.repository.UserRepository;
import com.envguard.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * Creates the first ADMINISTRATOR only when bootstrap env vars are set AND no admin exists.
 * Never hardcodes a default password.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final EnvGuardProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        EnvGuardProperties.Bootstrap bootstrap = properties.getBootstrap();
        if (bootstrap == null) {
            return;
        }
        String user = bootstrap.getAdminUser();
        String password = bootstrap.getAdminPassword();
        String email = bootstrap.getAdminEmail();

        if (!StringUtils.hasText(user) || !StringUtils.hasText(password) || !StringUtils.hasText(email)) {
            log.debug("Admin bootstrap skipped — ENVGUARD_BOOTSTRAP_ADMIN_* not fully set");
            return;
        }

        boolean adminExists = userRepository.findAll().stream()
                .anyMatch(u -> u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMINISTRATOR));
        if (adminExists) {
            log.info("Admin bootstrap skipped — an ADMINISTRATOR already exists");
            return;
        }

        if (userRepository.existsByUsername(user) || userRepository.existsByEmail(email)) {
            log.warn("Admin bootstrap skipped — username or email already exists");
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMINISTRATOR)
                .orElseThrow(() -> new IllegalStateException("ADMINISTRATOR role not seeded"));

        User admin = User.builder()
                .username(user.trim())
                .email(email.trim())
                .passwordHash(passwordEncoder.encode(password))
                .enabled(true)
                .roles(Set.of(adminRole))
                .build();
        userRepository.save(admin);

        auditService.record(null, admin.getId(), null, "ADMIN_BOOTSTRAPPED", "User",
                admin.getId().toString(), Map.of("username", admin.getUsername()));
        log.info("Bootstrapped initial ADMINISTRATOR user '{}'", admin.getUsername());
    }
}
