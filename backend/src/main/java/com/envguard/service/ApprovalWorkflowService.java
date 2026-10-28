package com.envguard.service;

import com.envguard.domain.entity.*;
import com.envguard.domain.enums.ApprovalDecision;
import com.envguard.domain.enums.DeploymentRequestStatus;
import com.envguard.dto.request.ApprovalActionRequest;
import com.envguard.exception.ApiException;
import com.envguard.repository.ApprovalRecordRepository;
import com.envguard.repository.DeploymentRequestRepository;
import com.envguard.repository.UserRepository;
import com.envguard.security.UserPrincipal;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Enforces deployment approval state machine.
 * Frontend JWT users can approve/reject only — they cannot transition APPROVED → DEPLOYING.
 * Only pipeline token paths may deploy.
 */
@Service
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private final DeploymentRequestRepository deploymentRequestRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final AuditService auditService;

    @Data
    @Builder
    public static class DeploymentRequestView {
        private String id;
        private String projectId;
        private String environmentId;
        private String revisionId;
        private String buildId;
        private String status;
        private String requesterIdentity;
        private String idempotencyKey;
        private String checksum;
        private String artifactDigest;
        private String expiresAt;
        private long approvedCount;
        private boolean requiresSecondApprover;
    }

    @Transactional(readOnly = true)
    public DeploymentRequestView get(UUID id, UserPrincipal principal) {
        DeploymentRequest req = load(id);
        projectService.requireAccess(req.getProject().getId(), principal);
        return toView(req);
    }

    @Transactional(readOnly = true)
    public List<DeploymentRequestView> pendingApprovals(UserPrincipal principal) {
        return deploymentRequestRepository.findByStatus(DeploymentRequestStatus.READY_FOR_APPROVAL).stream()
                .filter(r -> projectService.canAccess(r.getProject().getId(), principal))
                .filter(r -> isAuthorizedApprover(r.getEnvironment(), principal.getId())
                        || principal.hasRole(com.envguard.domain.enums.RoleName.ADMINISTRATOR))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public DeploymentRequestView approve(UUID id, ApprovalActionRequest request, UserPrincipal principal) {
        DeploymentRequest req = load(id);
        projectService.requireAccess(req.getProject().getId(), principal);
        assertReadyForApproval(req);
        assertNotExpired(req);
        assertChecksumAndArtifact(req, request.getChecksum(), request.getArtifactDigest());
        assertConfigUnchanged(req);

        if (!isAuthorizedApprover(req.getEnvironment(), principal.getId())
                && !principal.hasRole(com.envguard.domain.enums.RoleName.ADMINISTRATOR)) {
            throw ApiException.forbidden("User is not an authorized approver for this environment");
        }

        if (approvalRecordRepository.existsByDeploymentRequestIdAndApprover_IdAndDecision(
                req.getId(), principal.getId(), ApprovalDecision.APPROVED)) {
            throw ApiException.conflict("User already approved this request");
        }

        User approver = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        ApprovalRecord record = ApprovalRecord.builder()
                .deploymentRequest(req)
                .approver(approver)
                .decision(ApprovalDecision.APPROVED)
                .comment(request.getComment())
                .revisionId(req.getRevision().getId())
                .checksum(req.getRevision().getChecksum())
                .commitSha(req.getRevision().getSourceCommitSha())
                .artifactDigest(req.getBuild().getArtifactDigest())
                .environmentId(req.getEnvironment().getId())
                .build();
        approvalRecordRepository.save(record);

        long approved = approvalRecordRepository.countByDeploymentRequestIdAndDecision(
                req.getId(), ApprovalDecision.APPROVED);
        int required = req.getEnvironment().isRequiresSecondApprover() ? 2 : 1;

        if (approved >= required) {
            transition(req, DeploymentRequestStatus.APPROVED);
        }

        deploymentRequestRepository.save(req);
        auditService.record(req.getProject().getId(), principal.getId(), req.getEnvironment().getId(),
                "DEPLOYMENT_APPROVED", "DeploymentRequest", req.getId().toString(),
                Map.of("approvedCount", approved, "required", required));
        return toView(req);
    }

    @Transactional
    public DeploymentRequestView reject(UUID id, ApprovalActionRequest request, UserPrincipal principal) {
        DeploymentRequest req = load(id);
        projectService.requireAccess(req.getProject().getId(), principal);
        assertReadyForApproval(req);
        assertChecksumAndArtifact(req, request.getChecksum(), request.getArtifactDigest());

        if (!isAuthorizedApprover(req.getEnvironment(), principal.getId())
                && !principal.hasRole(com.envguard.domain.enums.RoleName.ADMINISTRATOR)) {
            throw ApiException.forbidden("User is not an authorized approver for this environment");
        }

        User approver = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        ApprovalRecord record = ApprovalRecord.builder()
                .deploymentRequest(req)
                .approver(approver)
                .decision(ApprovalDecision.REJECTED)
                .comment(request.getComment())
                .revisionId(req.getRevision().getId())
                .checksum(req.getRevision().getChecksum())
                .commitSha(req.getRevision().getSourceCommitSha())
                .artifactDigest(Optional.ofNullable(req.getBuild().getArtifactDigest()).orElse(""))
                .environmentId(req.getEnvironment().getId())
                .build();
        approvalRecordRepository.save(record);

        transition(req, DeploymentRequestStatus.REJECTED);
        deploymentRequestRepository.save(req);
        auditService.record(req.getProject().getId(), principal.getId(), req.getEnvironment().getId(),
                "DEPLOYMENT_REJECTED", "DeploymentRequest", req.getId().toString(), Map.of());
        return toView(req);
    }

    @Transactional
    public void markTimedOut(DeploymentRequest req) {
        if (req.getStatus() == DeploymentRequestStatus.READY_FOR_APPROVAL
                || req.getStatus() == DeploymentRequestStatus.APPROVED) {
            transition(req, DeploymentRequestStatus.TIMED_OUT);
            deploymentRequestRepository.save(req);
            auditService.record(req.getProject().getId(), null, req.getEnvironment().getId(),
                    "DEPLOYMENT_TIMED_OUT", "DeploymentRequest", req.getId().toString(), Map.of());
        }
    }

    @Transactional
    public void invalidateIfConfigChanged(DeploymentRequest req, String currentChecksum) {
        if (!Objects.equals(req.getRevision().getChecksum(), currentChecksum)) {
            if (!req.getStatus().isTerminal()) {
                transition(req, DeploymentRequestStatus.INVALIDATED);
                deploymentRequestRepository.save(req);
                auditService.record(req.getProject().getId(), null, req.getEnvironment().getId(),
                        "DEPLOYMENT_INVALIDATED", "DeploymentRequest", req.getId().toString(),
                        Map.of("reason", "config_checksum_mismatch"));
            }
        }
    }

    public boolean isAuthorizedApprover(Environment environment, UUID userId) {
        if (environment.getApprovers() == null || environment.getApprovers().isEmpty()) {
            // If no explicit approvers configured, any project member with access may approve (checked by caller)
            return true;
        }
        return environment.getApprovers().stream().anyMatch(u -> u.getId().equals(userId));
    }

    private void assertReadyForApproval(DeploymentRequest req) {
        if (req.getStatus() != DeploymentRequestStatus.READY_FOR_APPROVAL) {
            throw ApiException.conflict("Deployment request is not READY_FOR_APPROVAL (status=" + req.getStatus() + ")");
        }
    }

    private void assertNotExpired(DeploymentRequest req) {
        if (req.getExpiresAt() != null && Instant.now().isAfter(req.getExpiresAt())) {
            transition(req, DeploymentRequestStatus.TIMED_OUT);
            deploymentRequestRepository.save(req);
            throw ApiException.conflict("Deployment request has timed out");
        }
    }

    private void assertChecksumAndArtifact(DeploymentRequest req, String checksum, String artifactDigest) {
        if (!Objects.equals(req.getRevision().getChecksum(), checksum)) {
            transition(req, DeploymentRequestStatus.INVALIDATED);
            deploymentRequestRepository.save(req);
            throw ApiException.conflict("Checksum mismatch — request invalidated");
        }
        String buildDigest = req.getBuild().getArtifactDigest();
        if (buildDigest == null || !Objects.equals(buildDigest, artifactDigest)) {
            throw ApiException.conflict("Artifact digest mismatch");
        }
    }

    private void assertConfigUnchanged(DeploymentRequest req) {
        // Bound to immutable revision checksum — if caller supplies mismatch, invalidated above.
        // Additional guard: revision entity checksum must still match approval binding.
        if (req.getRevision().getChecksum() == null || req.getRevision().getChecksum().isBlank()) {
            throw ApiException.badRequest("Revision checksum missing");
        }
    }

    public void transition(DeploymentRequest req, DeploymentRequestStatus target) {
        if (!req.getStatus().canTransitionTo(target)) {
            throw ApiException.conflict("Illegal status transition " + req.getStatus() + " → " + target);
        }
        req.setStatus(target);
    }

    private DeploymentRequest load(UUID id) {
        return deploymentRequestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Deployment request not found"));
    }

    public DeploymentRequestView toView(DeploymentRequest req) {
        long approved = approvalRecordRepository.countByDeploymentRequestIdAndDecision(
                req.getId(), ApprovalDecision.APPROVED);
        return DeploymentRequestView.builder()
                .id(req.getId().toString())
                .projectId(req.getProject().getId().toString())
                .environmentId(req.getEnvironment().getId().toString())
                .revisionId(req.getRevision().getId().toString())
                .buildId(req.getBuild().getId().toString())
                .status(req.getStatus().name())
                .requesterIdentity(req.getRequesterIdentity())
                .idempotencyKey(req.getIdempotencyKey())
                .checksum(req.getRevision().getChecksum())
                .artifactDigest(req.getBuild().getArtifactDigest())
                .expiresAt(req.getExpiresAt() != null ? req.getExpiresAt().toString() : null)
                .approvedCount(approved)
                .requiresSecondApprover(req.getEnvironment().isRequiresSecondApprover())
                .build();
    }
}
