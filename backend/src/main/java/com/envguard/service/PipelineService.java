package com.envguard.service;

import com.envguard.config.EnvGuardProperties;
import com.envguard.domain.entity.*;
import com.envguard.domain.enums.*;
import com.envguard.dto.request.*;
import com.envguard.exception.ApiException;
import com.envguard.repository.*;
import com.envguard.util.EnvironmentNameParser;
import com.envguard.util.SecretRedactor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final BuildRecordRepository buildRecordRepository;
    private final DeploymentRequestRepository deploymentRequestRepository;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final ConfigurationRevisionRepository revisionRepository;
    private final ConfigurationService configurationService;
    private final DeploymentService deploymentService;
    private final AuditService auditService;
    private final EnvGuardProperties properties;

    @Data
    @Builder
    public static class BuildView {
        private String id;
        private String projectId;
        private String pipelineRunId;
        private String commitSha;
        private String branch;
        private String status;
        private String artifactDigest;
        private String artifactUrl;
        private String startedAt;
        private String finishedAt;
    }

    @Data
    @Builder
    public static class VerifyResult {
        private boolean verified;
        private String status;
        private List<String> mismatches;
    }

    @Transactional
    public BuildView registerOrUpdateBuild(RegisterBuildRequest request) {
        Project project = resolveProject(request.getProjectId(), request.getProjectName());

        Optional<BuildRecord> existing = buildRecordRepository.findByPipelineRunId(request.getPipelineRunId());
        BuildRecord build;
        if (existing.isPresent()) {
            build = existing.get();
            if (request.getStatus() != null) {
                build.setStatus(parseBuildStatus(request.getStatus()));
            }
            if (StringUtils.hasText(request.getArtifactDigest())) {
                build.setArtifactDigest(request.getArtifactDigest());
            }
            if (StringUtils.hasText(request.getArtifactUrl())) {
                build.setArtifactUrl(request.getArtifactUrl());
            }
            if (build.getStatus() == BuildStatus.SUCCEEDED || build.getStatus() == BuildStatus.FAILED
                    || build.getStatus() == BuildStatus.CANCELLED) {
                build.setFinishedAt(Instant.now());
            }
        } else {
            BuildStatus status = request.getStatus() != null
                    ? parseBuildStatus(request.getStatus()) : BuildStatus.RUNNING;
            build = BuildRecord.builder()
                    .project(project)
                    .commitSha(request.getCommitSha())
                    .branch(request.getBranch())
                    .status(status)
                    .artifactDigest(request.getArtifactDigest())
                    .artifactUrl(request.getArtifactUrl())
                    .pipelineRunId(request.getPipelineRunId())
                    .startedAt(Instant.now())
                    .build();
        }
        buildRecordRepository.save(build);
        auditService.record(project.getId(), null, null, "BUILD_REGISTERED", "BuildRecord",
                build.getId().toString(), Map.of("pipelineRunId", build.getPipelineRunId()));
        return toBuildView(build);
    }

    @Transactional
    public BuildView updateBuild(UUID buildId, UpdateBuildRequest request) {
        BuildRecord build = buildRecordRepository.findById(buildId)
                .orElseThrow(() -> ApiException.notFound("Build not found"));
        build.setStatus(request.getStatus());
        if (StringUtils.hasText(request.getArtifactDigest())) {
            build.setArtifactDigest(request.getArtifactDigest());
        }
        if (StringUtils.hasText(request.getArtifactUrl())) {
            build.setArtifactUrl(request.getArtifactUrl());
        }
        if (StringUtils.hasText(request.getLogs())) {
            build.setLogsRedacted(SecretRedactor.redact(request.getLogs()));
        }
        if (request.getStatus() == BuildStatus.SUCCEEDED || request.getStatus() == BuildStatus.FAILED
                || request.getStatus() == BuildStatus.CANCELLED) {
            build.setFinishedAt(Instant.now());
        }
        buildRecordRepository.save(build);
        return toBuildView(build);
    }

    @Transactional
    public DeploymentService.DeploymentView createDeployment(CreateDeploymentRequestDto request) {
        Optional<DeploymentRequest> existing =
                deploymentRequestRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return deploymentService.toView(existing.get());
        }

        Project project = resolveProject(request.getProjectId(), request.getProjectName());
        EnvironmentName envName = EnvironmentNameParser.parse(request.getEnvironment());
        Environment environment = environmentRepository.findByProjectIdAndName(project.getId(), envName)
                .orElseThrow(() -> ApiException.notFound("Environment not found: " + envName));

        BuildRecord build = resolveBuild(project, request);

        ConfigurationService.ValidationReport report = configurationService.validateAndStoreForPipeline(
                project, environment, request.getConfigYaml(), request.getConfig(),
                request.getCommitSha() != null ? request.getCommitSha() : build.getCommitSha(),
                request.getArtifactDigest() != null ? request.getArtifactDigest() : build.getArtifactDigest(),
                request.getRequesterIdentity());

        ConfigurationRevision revision = revisionRepository.findById(UUID.fromString(report.getRevisionId()))
                .orElseThrow(() -> ApiException.notFound("Revision not found"));

        // Ensure artifact digest is bound on revision if provided via build
        if (revision.getArtifactDigest() == null && build.getArtifactDigest() != null) {
            // Immutable field — already set at create; bind via build for approval matching
        }

        int timeout = environment.getApprovalTimeoutMinutes() > 0
                ? environment.getApprovalTimeoutMinutes()
                : properties.getApproval().getDefaultTimeoutMinutes();

        DeploymentRequest dr = DeploymentRequest.builder()
                .project(project)
                .environment(environment)
                .revision(revision)
                .build(build)
                .status(DeploymentRequestStatus.READY_FOR_APPROVAL)
                .requesterIdentity(request.getRequesterIdentity())
                .idempotencyKey(request.getIdempotencyKey())
                .expiresAt(Instant.now().plus(timeout, ChronoUnit.MINUTES))
                .build();
        try {
            dr = deploymentRequestRepository.saveAndFlush(dr);
        } catch (Exception e) {
            // Race on idempotency key
            return deploymentRequestRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .map(deploymentService::toView)
                    .orElseThrow(() -> ApiException.conflict("Duplicate deployment request"));
        }

        auditService.record(project.getId(), null, environment.getId(),
                "DEPLOYMENT_REQUESTED", "DeploymentRequest",
                dr.getId() != null ? dr.getId().toString() : null,
                Map.of("idempotencyKey", dr.getIdempotencyKey(), "checksum", revision.getChecksum()));

        return deploymentService.toView(dr);
    }

    @Transactional
    public DeploymentService.DeploymentView status(UUID id) {
        DeploymentRequest dr = deploymentService.loadAndExpire(id);
        return deploymentService.toView(dr);
    }

    @Transactional
    public VerifyResult verify(UUID id, VerifyDeploymentRequest request) {
        DeploymentRequest dr = deploymentService.loadAndExpire(id);
        List<String> mismatches = new ArrayList<>();

        if (dr.getStatus() != DeploymentRequestStatus.APPROVED
                && dr.getStatus() != DeploymentRequestStatus.DEPLOYING) {
            throw ApiException.badRequest("Deployment must be APPROVED before verify (status=" + dr.getStatus() + ")");
        }

        ConfigurationRevision revision = dr.getRevision();
        BuildRecord build = dr.getBuild();

        if (!Objects.equals(revision.getChecksum(), request.getChecksum())) {
            mismatches.add("checksum");
        }
        String expectedDigest = revision.getArtifactDigest() != null
                ? revision.getArtifactDigest() : build.getArtifactDigest();
        if (!Objects.equals(expectedDigest, request.getArtifactDigest())) {
            mismatches.add("artifactDigest");
        }
        if (!Objects.equals(revision.getSourceCommitSha(), request.getCommitSha())) {
            mismatches.add("commitSha");
        }
        EnvironmentName envName = EnvironmentNameParser.parse(request.getEnvironment());
        if (dr.getEnvironment().getName() != envName) {
            mismatches.add("environment");
        }

        // Also ensure approval record still matches
        List<ApprovalRecord> approvals = approvalRecordRepository.findByDeploymentRequestId(id).stream()
                .filter(a -> a.getDecision() == ApprovalDecision.APPROVED)
                .toList();
        for (ApprovalRecord a : approvals) {
            if (!Objects.equals(a.getChecksum(), revision.getChecksum())
                    || !Objects.equals(a.getArtifactDigest(), request.getArtifactDigest())
                    || !Objects.equals(a.getCommitSha(), revision.getSourceCommitSha())
                    || !Objects.equals(a.getEnvironmentId(), dr.getEnvironment().getId())
                    || !Objects.equals(a.getRevisionId(), revision.getId())) {
                mismatches.add("approvalBinding");
                break;
            }
        }

        if (!mismatches.isEmpty()) {
            dr.setStatus(DeploymentRequestStatus.INVALIDATED);
            deploymentRequestRepository.save(dr);
            auditService.record(dr.getProject().getId(), null, dr.getEnvironment().getId(),
                    "DEPLOYMENT_VERIFY_FAILED", "DeploymentRequest", id.toString(),
                    Map.of("mismatches", mismatches));
            return VerifyResult.builder()
                    .verified(false)
                    .status(dr.getStatus().name())
                    .mismatches(mismatches)
                    .build();
        }

        auditService.record(dr.getProject().getId(), null, dr.getEnvironment().getId(),
                "DEPLOYMENT_VERIFIED", "DeploymentRequest", id.toString(), Map.of());
        return VerifyResult.builder()
                .verified(true)
                .status(dr.getStatus().name())
                .mismatches(List.of())
                .build();
    }

    @Transactional
    public Map<String, Object> progress(UUID id, ProgressDeploymentRequest request) {
        DeploymentRequest dr = deploymentService.loadAndExpire(id);
        if (dr.getStatus() == DeploymentRequestStatus.APPROVED
                && dr.getStatus().canTransitionTo(DeploymentRequestStatus.DEPLOYING)) {
            dr.setStatus(DeploymentRequestStatus.DEPLOYING);
            deploymentRequestRepository.save(dr);
        }
        if (dr.getStatus().isTerminal()) {
            throw ApiException.badRequest("Deployment already terminal: " + dr.getStatus());
        }

        DeploymentRecord record = DeploymentRecord.builder()
                .deploymentRequest(dr)
                .status(request.getStatus() != null ? request.getStatus() : DeploymentRecordStatus.STARTED)
                .targetHost(request.getTargetHost())
                .healthStatus(request.getHealthStatus())
                .errorMessageRedacted(SecretRedactor.redact(request.getErrorMessage()))
                .startedAt(Instant.now())
                .build();
        deploymentRecordRepository.save(record);

        auditService.record(dr.getProject().getId(), null, dr.getEnvironment().getId(),
                "DEPLOYMENT_PROGRESS", "DeploymentRequest", id.toString(),
                Map.of("recordStatus", record.getStatus().name()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deploymentId", id.toString());
        response.put("status", dr.getStatus().name());
        response.put("recordId", record.getId().toString());
        return response;
    }

    @Transactional
    public DeploymentService.DeploymentView complete(UUID id, CompleteDeploymentRequest request) {
        DeploymentRequest dr = deploymentService.loadAndExpire(id);
        if (dr.getStatus().isTerminal()) {
            throw ApiException.badRequest("Deployment already terminal: " + dr.getStatus());
        }

        DeploymentRequestStatus target = Boolean.TRUE.equals(request.getSuccess())
                ? DeploymentRequestStatus.DEPLOYED : DeploymentRequestStatus.FAILED;
        if (dr.getStatus() == DeploymentRequestStatus.APPROVED) {
            dr.setStatus(DeploymentRequestStatus.DEPLOYING);
        }
        if (!dr.getStatus().canTransitionTo(target) && dr.getStatus() != DeploymentRequestStatus.DEPLOYING) {
            // allow DEPLOYING -> DEPLOYED/FAILED
            if (!(dr.getStatus() == DeploymentRequestStatus.DEPLOYING
                    && (target == DeploymentRequestStatus.DEPLOYED || target == DeploymentRequestStatus.FAILED))) {
                throw ApiException.conflict("Cannot complete from status " + dr.getStatus());
            }
        }
        dr.setStatus(target);
        deploymentRequestRepository.save(dr);

        DeploymentRecord record = DeploymentRecord.builder()
                .deploymentRequest(dr)
                .status(Boolean.TRUE.equals(request.getSuccess())
                        ? DeploymentRecordStatus.COMPLETED : DeploymentRecordStatus.FAILED)
                .targetHost(request.getTargetHost())
                .healthStatus(request.getHealthStatus())
                .durationMs(request.getDurationMs())
                .errorMessageRedacted(SecretRedactor.redact(request.getErrorMessage()))
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .build();
        deploymentRecordRepository.save(record);

        auditService.record(dr.getProject().getId(), null, dr.getEnvironment().getId(),
                Boolean.TRUE.equals(request.getSuccess()) ? "DEPLOYMENT_COMPLETED" : "DEPLOYMENT_FAILED",
                "DeploymentRequest", id.toString(), Map.of());

        return deploymentService.toView(dr);
    }

    private Project resolveProject(UUID projectId, String projectName) {
        if (projectId != null) {
            return projectRepository.findById(projectId)
                    .orElseThrow(() -> ApiException.notFound("Project not found"));
        }
        if (StringUtils.hasText(projectName)) {
            return projectRepository.findByName(projectName)
                    .orElseThrow(() -> ApiException.notFound("Project not found: " + projectName));
        }
        throw ApiException.badRequest("projectId or projectName is required");
    }

    private BuildRecord resolveBuild(Project project, CreateDeploymentRequestDto request) {
        if (request.getBuildId() != null) {
            BuildRecord build = buildRecordRepository.findById(request.getBuildId())
                    .orElseThrow(() -> ApiException.notFound("Build not found"));
            if (!build.getProject().getId().equals(project.getId())) {
                throw ApiException.badRequest("Build does not belong to project");
            }
            return build;
        }
        if (StringUtils.hasText(request.getPipelineRunId())) {
            return buildRecordRepository.findByPipelineRunId(request.getPipelineRunId())
                    .orElseGet(() -> {
                        BuildRecord created = BuildRecord.builder()
                                .project(project)
                                .commitSha(request.getCommitSha() != null ? request.getCommitSha() : "unknown")
                                .branch(request.getBranch() != null ? request.getBranch() : "main")
                                .status(BuildStatus.SUCCEEDED)
                                .artifactDigest(request.getArtifactDigest())
                                .pipelineRunId(request.getPipelineRunId())
                                .startedAt(Instant.now())
                                .finishedAt(Instant.now())
                                .build();
                        return buildRecordRepository.save(created);
                    });
        }
        // Auto-create ephemeral build for pipeline convenience
        String runId = "auto-" + UUID.randomUUID();
        BuildRecord created = BuildRecord.builder()
                .project(project)
                .commitSha(request.getCommitSha() != null ? request.getCommitSha() : "unknown")
                .branch(request.getBranch() != null ? request.getBranch() : "main")
                .status(BuildStatus.SUCCEEDED)
                .artifactDigest(request.getArtifactDigest())
                .pipelineRunId(runId)
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .build();
        return buildRecordRepository.save(created);
    }

    private BuildStatus parseBuildStatus(String status) {
        try {
            return BuildStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid build status: " + status);
        }
    }

    private BuildView toBuildView(BuildRecord build) {
        return BuildView.builder()
                .id(build.getId().toString())
                .projectId(build.getProject().getId().toString())
                .pipelineRunId(build.getPipelineRunId())
                .commitSha(build.getCommitSha())
                .branch(build.getBranch())
                .status(build.getStatus().name())
                .artifactDigest(build.getArtifactDigest())
                .artifactUrl(build.getArtifactUrl())
                .startedAt(build.getStartedAt() != null ? build.getStartedAt().toString() : null)
                .finishedAt(build.getFinishedAt() != null ? build.getFinishedAt().toString() : null)
                .build();
    }
}
