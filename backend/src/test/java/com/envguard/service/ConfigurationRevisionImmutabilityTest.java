package com.envguard.service;

import com.envguard.domain.entity.ConfigurationRevision;
import com.envguard.domain.entity.Environment;
import com.envguard.domain.entity.Project;
import com.envguard.domain.entity.User;
import com.envguard.domain.enums.EnvironmentName;
import com.envguard.domain.enums.RevisionApprovalStatus;
import com.envguard.domain.enums.ValidationStatus;
import com.envguard.dto.request.CreateProjectRequest;
import com.envguard.dto.request.RegisterRequest;
import com.envguard.repository.ConfigurationRevisionRepository;
import com.envguard.repository.EnvironmentRepository;
import com.envguard.repository.UserRepository;
import com.envguard.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.envguard.EnvGuardApplication.class)
@ActiveProfiles("test")
@Transactional
class ConfigurationRevisionImmutabilityTest {

    @Autowired AuthService authService;
    @Autowired ProjectService projectService;
    @Autowired UserRepository userRepository;
    @Autowired EnvironmentRepository environmentRepository;
    @Autowired ConfigurationRevisionRepository revisionRepository;
    @Autowired EntityManager entityManager;

    @Test
    void configYamlAndChecksumAreNotUpdatedAfterPersist() {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername("immut_user");
        reg.setEmail("immut_user@example.com");
        reg.setPassword("password123");
        authService.register(reg);
        User user = userRepository.findByUsername("immut_user").orElseThrow();
        UserPrincipal principal = new UserPrincipal(user);

        CreateProjectRequest projReq = new CreateProjectRequest();
        projReq.setName("immut-" + UUID.randomUUID().toString().substring(0, 8));
        ProjectService.ProjectView pv = projectService.create(projReq, principal);
        Project project = projectService.requireAccess(UUID.fromString(pv.getId()), principal);
        Environment env = environmentRepository.findByProjectIdAndName(project.getId(), EnvironmentName.DEV).orElseThrow();

        String originalYaml = "environment: dev\nvariables:\n  APP_HOST: original\nsecretReferences: {}\n";
        String checksum = "c".repeat(64);

        ConfigurationRevision revision = revisionRepository.save(ConfigurationRevision.builder()
                .project(project)
                .environment(env)
                .sourceCommitSha("sha1")
                .checksum(checksum)
                .authorIdentity(user.getUsername())
                .configYaml(originalYaml)
                .validationStatus(ValidationStatus.VALID)
                .approvalStatus(RevisionApprovalStatus.PENDING)
                .build());

        UUID id = revision.getId();
        entityManager.flush();
        entityManager.clear();

        ConfigurationRevision loaded = revisionRepository.findById(id).orElseThrow();
        // Attempt mutation in memory — updatable=false columns must not be written on flush
        loaded.setApprovalStatus(RevisionApprovalStatus.APPROVED);
        loaded.setConfigYaml("environment: dev\nvariables:\n  APP_HOST: tampered\nsecretReferences: {}\n");
        loaded.setChecksum("d".repeat(64));
        revisionRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        ConfigurationRevision after = revisionRepository.findById(id).orElseThrow();
        assertThat(after.getConfigYaml()).isEqualTo(originalYaml);
        assertThat(after.getChecksum()).isEqualTo(checksum);
        assertThat(after.getApprovalStatus()).isEqualTo(RevisionApprovalStatus.APPROVED);
    }
}
