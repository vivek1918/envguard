package com.envguard.service;

import com.envguard.domain.entity.*;
import com.envguard.domain.enums.*;
import com.envguard.dto.request.ApprovalActionRequest;
import com.envguard.dto.request.CreateProjectRequest;
import com.envguard.dto.request.RegisterRequest;
import com.envguard.exception.ApiException;
import com.envguard.repository.*;
import com.envguard.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = com.envguard.EnvGuardApplication.class)
@ActiveProfiles("test")
@Transactional
class ApprovalWorkflowServiceTest {

    @Autowired AuthService authService;
    @Autowired ProjectService projectService;
    @Autowired DeploymentService deploymentService;
    @Autowired EnvironmentRepository environmentRepository;
    @Autowired ConfigurationRevisionRepository revisionRepository;
    @Autowired BuildRecordRepository buildRecordRepository;
    @Autowired DeploymentRequestRepository deploymentRequestRepository;
    @Autowired UserRepository userRepository;

    private UserPrincipal adminPrincipal;
    private UserPrincipal otherPrincipal;
    private Project project;
    private Environment staging;
    private Environment production;
    private ConfigurationRevision revision;
    private BuildRecord build;
    private String checksum;

    @BeforeEach
    void setUp() {
        RegisterRequest adminReg = new RegisterRequest();
        adminReg.setUsername("approver_admin");
        adminReg.setEmail("approver_admin@example.com");
        adminReg.setPassword("password123");
        authService.register(adminReg);
        User admin = userRepository.findByUsername("approver_admin").orElseThrow();
        adminPrincipal = new UserPrincipal(admin);

        RegisterRequest otherReg = new RegisterRequest();
        otherReg.setUsername("other_user");
        otherReg.setEmail("other_user@example.com");
        otherReg.setPassword("password123");
        authService.register(otherReg);
        User other = userRepository.findByUsername("other_user").orElseThrow();
        otherPrincipal = new UserPrincipal(other);

        CreateProjectRequest projReq = new CreateProjectRequest();
        projReq.setName("approval-test-" + UUID.randomUUID().toString().substring(0, 8));
        projReq.setDescription("test");
        ProjectService.ProjectView pv = projectService.create(projReq, adminPrincipal);
        project = projectService.requireAccess(UUID.fromString(pv.getId()), adminPrincipal);

        staging = environmentRepository.findByProjectIdAndName(project.getId(), EnvironmentName.STAGING).orElseThrow();
        production = environmentRepository.findByProjectIdAndName(project.getId(), EnvironmentName.PRODUCTION).orElseThrow();
        production.setRequiresSecondApprover(true);
        production.getApprovers().add(admin);
        production.getApprovers().add(other);
        environmentRepository.save(production);

        staging.getApprovers().add(admin);
        environmentRepository.save(staging);

        checksum = "a".repeat(64);
        revision = revisionRepository.save(ConfigurationRevision.builder()
                .project(project)
                .environment(staging)
                .sourceCommitSha("abc123")
                .checksum(checksum)
                .authorIdentity("ci")
                .configYaml("environment: staging\nvariables:\n  APP_HOST: x\nsecretReferences: {}\n")
                .validationStatus(ValidationStatus.VALID)
                .approvalStatus(RevisionApprovalStatus.PENDING)
                .artifactDigest("sha256:artifact1")
                .build());

        build = buildRecordRepository.save(BuildRecord.builder()
                .project(project)
                .commitSha("abc123")
                .branch("main")
                .status(BuildStatus.SUCCEEDED)
                .artifactDigest("sha256:artifact1")
                .pipelineRunId("run-" + UUID.randomUUID())
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .build());
    }

    private DeploymentRequest readyRequest(Environment env, ConfigurationRevision rev) {
        return deploymentRequestRepository.save(DeploymentRequest.builder()
                .project(project)
                .environment(env)
                .revision(rev)
                .build(build)
                .status(DeploymentRequestStatus.READY_FOR_APPROVAL)
                .requesterIdentity("pipeline")
                .idempotencyKey("idem-" + UUID.randomUUID())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build());
    }

    private ApprovalActionRequest action() {
        ApprovalActionRequest req = new ApprovalActionRequest();
        req.setChecksum(checksum);
        req.setArtifactDigest("sha256:artifact1");
        req.setComment("LGTM");
        return req;
    }

    @Test
    void approveTransitionsToApprovedWhenSingleApprover() {
        DeploymentRequest req = readyRequest(staging, revision);
        var view = deploymentService.approve(req.getId(), action(), adminPrincipal);
        assertThat(view.getStatus()).isEqualTo("APPROVED");
        assertThat(view.getApprovalCount()).isEqualTo(1);
    }

    @Test
    void rejectSetsRejected() {
        DeploymentRequest req = readyRequest(staging, revision);
        var view = deploymentService.reject(req.getId(), action(), adminPrincipal);
        assertThat(view.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void timeoutMarksTimedOut() {
        DeploymentRequest req = readyRequest(staging, revision);
        req.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        deploymentRequestRepository.save(req);
        deploymentService.expireIfNeeded(req, Instant.now());
        assertThat(deploymentRequestRepository.findById(req.getId()).orElseThrow().getStatus())
                .isEqualTo(DeploymentRequestStatus.TIMED_OUT);
    }

    @Test
    void checksumMismatchRejected() {
        DeploymentRequest req = readyRequest(staging, revision);
        ApprovalActionRequest bad = action();
        bad.setChecksum("b".repeat(64));
        assertThatThrownBy(() -> deploymentService.approve(req.getId(), bad, adminPrincipal))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Checksum");
    }

    @Test
    void artifactMismatchRejected() {
        DeploymentRequest req = readyRequest(staging, revision);
        ApprovalActionRequest bad = action();
        bad.setArtifactDigest("sha256:wrong");
        assertThatThrownBy(() -> deploymentService.approve(req.getId(), bad, adminPrincipal))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Artifact");
    }

    @Test
    void unauthorizedApproverForbidden() {
        staging.setApprovers(new java.util.HashSet<>(Set.of(userRepository.findByUsername("approver_admin").orElseThrow())));
        environmentRepository.save(staging);
        DeploymentRequest req = readyRequest(staging, revision);
        assertThatThrownBy(() -> deploymentService.approve(req.getId(), action(), otherPrincipal))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void productionRequiresSecondApprover() {
        ConfigurationRevision prodRev = revisionRepository.save(ConfigurationRevision.builder()
                .project(project)
                .environment(production)
                .sourceCommitSha("abc123")
                .checksum(checksum)
                .authorIdentity("ci")
                .configYaml("environment: production\nvariables:\n  APP_HOST: x\nsecretReferences: {}\n")
                .validationStatus(ValidationStatus.VALID)
                .approvalStatus(RevisionApprovalStatus.PENDING)
                .artifactDigest("sha256:artifact1")
                .build());
        DeploymentRequest req = readyRequest(production, prodRev);

        var first = deploymentService.approve(req.getId(), action(), adminPrincipal);
        assertThat(first.getStatus()).isEqualTo("READY_FOR_APPROVAL");
        assertThat(first.getApprovalCount()).isEqualTo(1);

        var second = deploymentService.approve(req.getId(), action(), otherPrincipal);
        assertThat(second.getStatus()).isEqualTo("APPROVED");
        assertThat(second.getApprovalCount()).isEqualTo(2);
    }

    @Test
    void cannotSkipApprovalDirectlyToDeployed() {
        DeploymentRequest req = readyRequest(staging, revision);
        deploymentService.approve(req.getId(), action(), adminPrincipal);
        DeploymentRequest approved = deploymentRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(DeploymentRequestStatus.APPROVED);
        assertThat(approved.getStatus().canTransitionTo(DeploymentRequestStatus.DEPLOYED)).isFalse();
        assertThat(approved.getStatus().canTransitionTo(DeploymentRequestStatus.DEPLOYING)).isTrue();
    }
}
