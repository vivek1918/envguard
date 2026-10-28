package com.envguard.service;

import com.envguard.dto.request.LoginRequest;
import com.envguard.dto.request.RegisterRequest;
import com.envguard.exception.ApiException;
import com.envguard.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = com.envguard.EnvGuardApplication.class)
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void clearUsers() {
        // Shared in-memory H2 may retain users from other test classes in the same JVM.
        entityManager.createNativeQuery("DELETE FROM user_roles").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM project_members").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM environment_approvers").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM approval_records").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM deployment_records").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM deployment_requests").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM configuration_variables").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM configuration_revisions").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM build_records").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM repository_integrations").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM environments").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM projects").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM audit_events").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void firstUserBecomesAdministrator() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("admin1");
        req.setEmail("admin1@example.com");
        req.setPassword("password123");

        AuthService.AuthResult result = authService.register(req);
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getUser().getRoles()).contains("ADMINISTRATOR");
    }

    @Test
    void subsequentUserBecomesDeveloper() {
        RegisterRequest first = new RegisterRequest();
        first.setUsername("admin2");
        first.setEmail("admin2@example.com");
        first.setPassword("password123");
        authService.register(first);

        RegisterRequest second = new RegisterRequest();
        second.setUsername("dev1");
        second.setEmail("dev1@example.com");
        second.setPassword("password123");
        AuthService.AuthResult result = authService.register(second);
        assertThat(result.getUser().getRoles()).contains("DEVELOPER");
        assertThat(result.getUser().getRoles()).doesNotContain("ADMINISTRATOR");
    }

    @Test
    void loginReturnsToken() {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername("loginuser");
        reg.setEmail("loginuser@example.com");
        reg.setPassword("password123");
        authService.register(reg);

        LoginRequest login = new LoginRequest();
        login.setUsername("loginuser");
        login.setPassword("password123");
        AuthService.AuthResult result = authService.login(login);
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getUser().getUsername()).isEqualTo("loginuser");
    }

    @Test
    void duplicateUsernameRejected() {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername("dup");
        reg.setEmail("dup1@example.com");
        reg.setPassword("password123");
        authService.register(reg);

        RegisterRequest dup = new RegisterRequest();
        dup.setUsername("dup");
        dup.setEmail("dup2@example.com");
        dup.setPassword("password123");
        assertThatThrownBy(() -> authService.register(dup)).isInstanceOf(ApiException.class);
    }
}
