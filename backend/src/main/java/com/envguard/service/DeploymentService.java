package com.envguard.service;

import com.envguard.config.EnvGuardProperties;
import com.envguard.domain.entity.*;
import com.envguard.domain.enums.*;
import com.envguard.dto.request.ApprovalActionRequest;
import com.envguard.exception.ApiException;
import com.envguard.repository.*;
import com.envguard.security.UserPrincipal;
import com.envguard.util.EnvironmentNameParser;
import com.envguard.util.SecretRedactor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRequestRepository deploymentRequestRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final AuditEventRepository auditEventRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final AuditService auditService;
    private final EnvGuardProperties properties;

    @Data
    @Builder
    public static class DeploymentView {
        private String id;
        private String projectId;
        private String projectName;
        private String environmentId;
        private String environment;
        private String revisionId;
        private String checksum;
        private String artifactDigest;
        private String commitSha;
        private String buildId;
        private String status;
        private String requesterIdentity;
        private String idempotencyKey;
        private String expiresAt;
        private String createdAt;
        private String updatedAt;
        private long approvalCount;
        private boolean requiresSecondApprover;
    }

    @Transactional(readOnly = true)
    public List<DeploymentView> pendingApprovals(UserPrincipal principal, UUID projectId, String environment) {
        List<DeploymentRequest> pending = deploymentRequestRepository
                .findByStatusIn(List.of(DeploymentRequestStatus.READY_FOR_APPROVAL));
        Instant now = Instant.now();
        List<DeploymentView> result = new ArrayList<>();
        for (DeploymentRequest dr : pending) {
            expireIfNeeded(dr, now);
            if (dr.getStatus() != DeploymentRequestStatus.READY_FOR_APPROVAL) {
                continue;
            }
            if (!projectService.canAccess(dr.getProject().getId(), principal)) {
                continue;
            }
            if (projectId != null && !dr.getProject().getId().equals(projectId)) {
                continue;
            }
            if (environment != null && !environment.isBlank()) {
                EnvironmentName envName = EnvironmentNameParser.parse(environment);
                if (dr.getEnvironment().getName() != envName) {
                    continue;
                }
            }
            result.add(toView(dr));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public DeploymentView get(UUID id, UserPrincipal principal) {
        DeploymentRequest dr = loadAndExpire(id);
        projectService.requireAccess(dr.getProject().getId(), principal);
        return toView(dr);
    }

    @Transactional
    public DeploymentView approve(UUID id, ApprovalActionRequest request, UserPrincipal principal) {
        DeploymentRequest dr = loadAndExpire(id);
        requireProjectAccessOrEnvApprover(dr, principal);

        if (principal.hasRole(RoleName.VIEWER) && !principal.hasRole(RoleName.DEVELOPER)
                && !principal.hasRole(RoleName.ADMINISTRATOR)) {
            throw ApiException.forbidden("Viewers cannot approve deployments");
        }

        assertCanApprove(dr, principal);

        if (dr.getStatus() != DeploymentRequestStatus.READY_FOR_APPROVAL) {
            throw ApiException.badRequest("Deployment is not awaiting approval (status=" + dr.getStatus() + ")");
        }
        if (request.getChecksum() == null || request.getChecksum().isBlank()
                || request.getArtifactDigest() == null || request.getArtifactDigest().isBlank()) {
            throw ApiException.badRequest("checksum and artifactDigest are required for approval");
        }

        ConfigurationRevision revision = dr.getRevision();
        BuildRecord build = dr.getBuild();

        if (!Objects.equals(revision.getChecksum(), request.getChecksum())) {
            throw ApiException.badRequest("Checksum mismatch — approval must bind to exact revision checksum");
        }
        String expectedDigest = revision.getArtifactDigest() != null
                ? revision.getArtifactDigest() : build.getArtifactDigest();
        if (!Objects.equals(expectedDigest, request.getArtifactDigest())) {
            throw ApiException.badRequest("Artifact digest mismatch — approval must bind to exact artifact");
        }

        if (approvalRecordRepository.existsByDeploymentRequestIdAndApprover_IdAndDecision(
                dr.getId(), principal.getId(), ApprovalDecision.APPROVED)) {
            throw ApiException.conflict("You have already approved this deployment");
        }

        // Production self-approval ban
        if (dr.getEnvironment().getName() == EnvironmentName.PRODUCTION
                && properties.getApproval().isRejectSelfApprovalProduction()
                && principal.getUsername().equalsIgnoreCase(dr.getRequesterIdentity())) {
            throw ApiException.forbidden("Self-approval is not allowed for production deployments");
        }

        User approver = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        ApprovalRecord record = ApprovalRecord.builder()
                .deploymentRequest(dr)
                .approver(approver)
                .decision(ApprovalDecision.APPROVED)
                .comment(SecretRedactor.redact(request.getComment()))
                .revisionId(revision.getId())
                .checksum(revision.getChecksum())
                .commitSha(revision.getSourceCommitSha())
                .artifactDigest(request.getArtifactDigest())
                .environmentId(dr.getEnvironment().getId())
                .build();
        approvalRecordRepository.save(record);

        long approvals = approvalRecordRepository.countByDeploymentRequestIdAndDecision(
                dr.getId(), ApprovalDecision.APPROVED);
        boolean needSecond = dr.getEnvironment().isRequiresSecondApprover();
        int required = needSecond ? 2 : 1;

        if (approvals >= required) {
            if (!dr.getStatus().canTransitionTo(DeploymentRequestStatus.APPROVED)) {
                throw ApiException.conflict("Illegal status transition");
            }
            dr.setStatus(DeploymentRequestStatus.APPROVED);
            revision.setApprovalStatus(RevisionApprovalStatus.APPROVED);
            deploymentRequestRepository.save(dr);
        }

        auditService.record(dr.getProject().getId(), principal.getId(), dr.getEnvironment().getId(),
                "DEPLOYMENT_APPROVED", "DeploymentRequest", dr.getId().toString(),
                Map.of("approvals", approvals, "required", required, "checksum", revision.getChecksum()));

        return toView(dr);
    }

    @Transactional
    public DeploymentView reject(UUID id, ApprovalActionRequest request, UserPrincipal principal) {
        DeploymentRequest dr = loadAndExpire(id);
        requireProjectAccessOrEnvApprover(dr, principal);
        assertCanApprove(dr, principal);

        if (dr.getStatus() != DeploymentRequestStatus.READY_FOR_APPROVAL
                && dr.getStatus() != DeploymentRequestStatus.APPROVED) {
            throw ApiException.badRequest("Deployment cannot be rejected in status " + dr.getStatus());
        }

        ConfigurationRevision revision = dr.getRevision();
        if (request.getChecksum() != null && !request.getChecksum().equals(revision.getChecksum())) {
            throw ApiException.badRequest("Checksum mismatch");
        }

        User approver = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        ApprovalRecord record = ApprovalRecord.builder()
                .deploymentRequest(dr)
                .approver(approver)
                .decision(ApprovalDecision.REJECTED)
                .comment(SecretRedactor.redact(request.getComment()))
                .revisionId(revision.getId())
                .checksum(revision.getChecksum())
                .commitSha(revision.getSourceCommitSha())
                .artifactDigest(request.getArtifactDigest() != null ? request.getArtifactDigest()
                        : Optional.ofNullable(revision.getArtifactDigest()).orElse("n/a"))
                .environmentId(dr.getEnvironment().getId())
                .build();
        approvalRecordRepository.save(record);

        dr.setStatus(DeploymentRequestStatus.REJECTED);
        revision.setApprovalStatus(RevisionApprovalStatus.REJECTED);
        deploymentRequestRepository.save(dr);

        auditService.record(dr.getProject().getId(), principal.getId(), dr.getEnvironment().getId(),
                "DEPLOYMENT_REJECTED", "DeploymentRequest", dr.getId().toString(),
                Map.of("comment", request.getComment() == null ? "" : SecretRedactor.redact(request.getComment())));

        return toView(dr);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> auditTrail(UUID id, UserPrincipal principal) {
        DeploymentRequest dr = deploymentRequestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Deployment not found"));
        projectService.requireAccess(dr.getProject().getId(), principal);

        List<ApprovalRecord> approvals = approvalRecordRepository.findByDeploymentRequestId(id);
        List<AuditEvent> events = auditEventRepository.findAll().stream()
                .filter(e -> "DeploymentRequest".equals(e.getEntityType())
                        && id.toString().equals(e.getEntityId()))
                .sorted(Comparator.comparing(AuditEvent::getCreatedAt))
                .toList();

        List<Map<String, Object>> trail = new ArrayList<>();
        for (ApprovalRecord a : approvals) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "APPROVAL");
            m.put("decision", a.getDecision().name());
            m.put("approverId", a.getApprover().getId().toString());
            m.put("approver", a.getApprover().getUsername());
            m.put("checksum", a.getChecksum());
            m.put("artifactDigest", a.getArtifactDigest());
            m.put("commitSha", a.getCommitSha());
            m.put("comment", a.getComment());
            m.put("createdAt", a.getCreatedAt().toString());
            trail.add(m);
        }
        for (AuditEvent e : events) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "AUDIT");
            m.put("action", e.getAction());
            m.put("detailsJson", e.getDetailsJson());
            m.put("correlationId", e.getCorrelationId());
            m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
            trail.add(m);
        }
        return trail;
    }

    private void requireProjectAccessOrEnvApprover(DeploymentRequest dr, UserPrincipal principal) {
        if (projectService.canAccess(dr.getProject().getId(), principal)) {
            return;
        }
        Set<User> envApprovers = dr.getEnvironment().getApprovers();
        if (envApprovers != null && envApprovers.stream().anyMatch(u -> u.getId().equals(principal.getId()))) {
            return;
        }
        throw ApiException.forbidden("Not a project member");
    }

    private void assertCanApprove(DeploymentRequest dr, UserPrincipal principal) {
        if (principal.hasRole(RoleName.ADMINISTRATOR)) {
            return;
        }
        // Explicit environment approvers take precedence when configured
        Set<User> envApprovers = dr.getEnvironment().getApprovers();
        if (envApprovers != null && !envApprovers.isEmpty()) {
            boolean listed = envApprovers.stream().anyMatch(u -> u.getId().equals(principal.getId()));
            if (!listed) {
                throw ApiException.forbidden("You are not an approver for this environment");
            }
            return;
        }

        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(dr.getProject().getId(), principal.getId())
                .orElseThrow(() -> ApiException.forbidden("Not a project member"));
        if (member.getRole() == ProjectMemberRole.VIEWER) {
            throw ApiException.forbidden("Viewers cannot approve deployments");
        }
        if (principal.hasRole(RoleName.VIEWER)
                && !principal.hasRole(RoleName.DEVELOPER)
                && !principal.hasRole(RoleName.ADMINISTRATOR)) {
            throw ApiException.forbidden("Viewers cannot approve deployments");
        }
    }

    public DeploymentRequest loadAndExpire(UUID id) {
        DeploymentRequest dr = deploymentRequestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Deployment not found"));
        expireIfNeeded(dr, Instant.now());
        return dr;
    }

    public void expireIfNeeded(DeploymentRequest dr, Instant now) {
        if (dr.getStatus() == DeploymentRequestStatus.READY_FOR_APPROVAL
                && dr.getExpiresAt() != null
                && dr.getExpiresAt().isBefore(now)
                && dr.getStatus().canTransitionTo(DeploymentRequestStatus.TIMED_OUT)) {
            dr.setStatus(DeploymentRequestStatus.TIMED_OUT);
            deploymentRequestRepository.save(dr);
            auditService.record(dr.getProject().getId(), null, dr.getEnvironment().getId(),
                    "DEPLOYMENT_TIMED_OUT", "DeploymentRequest", dr.getId().toString(), Map.of());
        }
    }

    public DeploymentView toView(DeploymentRequest dr) {
        long approvals = approvalRecordRepository.countByDeploymentRequestIdAndDecision(
                dr.getId(), ApprovalDecision.APPROVED);
        return DeploymentView.builder()
                .id(dr.getId().toString())
                .projectId(dr.getProject().getId().toString())
                .projectName(dr.getProject().getName())
                .environmentId(dr.getEnvironment().getId().toString())
                .environment(EnvironmentNameParser.toApiName(dr.getEnvironment().getName()))
                .revisionId(dr.getRevision().getId().toString())
                .checksum(dr.getRevision().getChecksum())
                .artifactDigest(dr.getRevision().getArtifactDigest() != null
                        ? dr.getRevision().getArtifactDigest()
                        : dr.getBuild().getArtifactDigest())
                .commitSha(dr.getRevision().getSourceCommitSha())
                .buildId(dr.getBuild().getId().toString())
                .status(dr.getStatus().name())
                .requesterIdentity(dr.getRequesterIdentity())
                .idempotencyKey(dr.getIdempotencyKey())
                .expiresAt(dr.getExpiresAt() != null ? dr.getExpiresAt().toString() : null)
                .createdAt(dr.getCreatedAt() != null ? dr.getCreatedAt().toString() : null)
                .updatedAt(dr.getUpdatedAt() != null ? dr.getUpdatedAt().toString() : null)
                .approvalCount(approvals)
                .requiresSecondApprover(dr.getEnvironment().isRequiresSecondApprover())
                .build();
    }
}
