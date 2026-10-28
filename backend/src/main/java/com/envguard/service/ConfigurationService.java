package com.envguard.service;

import com.envguard.domain.entity.ConfigurationRevision;
import com.envguard.domain.entity.ConfigurationVariable;
import com.envguard.domain.entity.Environment;
import com.envguard.domain.entity.Project;
import com.envguard.domain.enums.EnvironmentName;
import com.envguard.domain.enums.RevisionApprovalStatus;
import com.envguard.domain.enums.ValidationStatus;
import com.envguard.dto.request.ValidateConfigRequest;
import com.envguard.exception.ApiException;
import com.envguard.repository.ConfigurationRevisionRepository;
import com.envguard.repository.EnvironmentRepository;
import com.envguard.repository.UserRepository;
import com.envguard.security.UserPrincipal;
import com.envguard.util.EnvironmentNameParser;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private final ConfigurationValidationService validationService;
    private final ConfigurationRevisionRepository revisionRepository;
    private final EnvironmentRepository environmentRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final AuditService auditService;

    @Data
    @Builder
    public static class RevisionView {
        private String id;
        private String projectId;
        private String environmentId;
        private String environment;
        private String sourceCommitSha;
        private String checksum;
        private String authorIdentity;
        private String validationStatus;
        private String approvalStatus;
        private String artifactDigest;
        private String createdAt;
        private List<VariableView> variables;
    }

    @Data
    @Builder
    public static class VariableView {
        private String name;
        private String value;
        private boolean secretRef;
        private String secretReference;
        private String secretVersion;
    }

    @Data
    @Builder
    public static class ValidationReport {
        private boolean valid;
        private String revisionId;
        private String checksum;
        private String environment;
        private List<String> errors;
        private List<String> warnings;
        private ConfigurationValidationService.ConfigDiff diff;
        private Map<String, String> secretAvailability;
        private RevisionView revision;
    }

    @Transactional
    public ValidationReport validateAndStore(UUID projectId, ValidateConfigRequest request, UserPrincipal principal) {
        Project project = projectService.requireAccess(projectId, principal);
        EnvironmentName envName = EnvironmentNameParser.parse(request.getEnvironment());
        Environment environment = environmentRepository.findByProjectIdAndName(projectId, envName)
                .orElseThrow(() -> ApiException.notFound("Environment not found: " + envName));

        String yaml = validationService.resolveYaml(request.getConfigYaml(), request.getConfig());
        ConfigurationValidationService.ValidationResult result =
                validationService.validate(yaml, EnvironmentNameParser.toApiName(envName), Set.of());

        Optional<ConfigurationRevision> previous = revisionRepository
                .findFirstByProjectIdAndEnvironmentIdOrderByCreatedAtDesc(projectId, environment.getId());

        ConfigurationValidationService.ConfigDiff diff;
        if (previous.isPresent()) {
            var prevResult = validationService.validate(previous.get().getConfigYaml(), Set.of());
            diff = validationService.diff(prevResult, result);
        } else {
            diff = ConfigurationValidationService.ConfigDiff.builder()
                    .addedVariables(result.getVariables() == null ? List.of()
                            : result.getVariables().keySet().stream().sorted().toList())
                    .removedVariables(List.of())
                    .changedVariables(List.of())
                    .addedSecretRefs(result.getSecretReferences() == null ? List.of()
                            : result.getSecretReferences().keySet().stream().sorted().toList())
                    .removedSecretRefs(List.of())
                    .changedSecretRefs(List.of())
                    .build();
        }

        if (yaml == null || yaml.isBlank()) {
            return ValidationReport.builder()
                    .valid(false)
                    .errors(result.getErrors())
                    .warnings(result.getWarnings())
                    .diff(diff)
                    .secretAvailability(result.getSecretAvailability())
                    .build();
        }

        // Immutable revision — stored once; never updated afterward.
        ConfigurationRevision revision = ConfigurationRevision.builder()
                .project(project)
                .environment(environment)
                .sourceCommitSha(request.getCommitSha() == null || request.getCommitSha().isBlank()
                        ? "unknown" : request.getCommitSha())
                .checksum(result.getChecksum() == null ? "invalid" : result.getChecksum())
                .authorUser(principal == null ? null : userRepository.findById(principal.getId()).orElse(null))
                .authorIdentity(principal == null ? "system" : principal.getUsername())
                .configYaml(yaml)
                .validationStatus(result.isValid() ? ValidationStatus.VALID : ValidationStatus.INVALID)
                .approvalStatus(RevisionApprovalStatus.PENDING)
                .artifactDigest(request.getArtifactDigest())
                .build();

        List<ConfigurationVariable> vars = new ArrayList<>();
        if (result.getVariables() != null) {
            result.getVariables().forEach((name, value) -> vars.add(ConfigurationVariable.builder()
                    .revision(revision)
                    .varName(name)
                    .varValue(value)
                    .secretRef(false)
                    .build()));
        }
        if (result.getSecretReferences() != null) {
            result.getSecretReferences().forEach((name, ref) -> vars.add(ConfigurationVariable.builder()
                    .revision(revision)
                    .varName(name)
                    .varValue(null)
                    .secretRef(true)
                    .secretReference(ref)
                    .build()));
        }
        revision.setVariables(vars);
        revisionRepository.save(revision);

        UUID actorId = principal == null ? null : principal.getId();
        auditService.record(project.getId(), actorId, environment.getId(),
                "CONFIG_VALIDATED", "ConfigurationRevision", revision.getId().toString(),
                Map.of("checksum", revision.getChecksum(), "valid", result.isValid()));

        return ValidationReport.builder()
                .valid(result.isValid())
                .revisionId(revision.getId().toString())
                .checksum(revision.getChecksum())
                .environment(EnvironmentNameParser.toApiName(envName))
                .errors(result.getErrors())
                .warnings(result.getWarnings())
                .diff(diff)
                .secretAvailability(result.getSecretAvailability())
                .revision(toView(revision))
                .build();
    }

    /**
     * Pipeline path — validate + store without a user principal.
     */
    @Transactional
    public ValidationReport validateAndStoreForPipeline(Project project, Environment environment,
                                                        String configYaml, Map<String, Object> config,
                                                        String commitSha, String artifactDigest,
                                                        String authorIdentity) {
        String yaml = validationService.resolveYaml(configYaml, config);
        ConfigurationValidationService.ValidationResult result = validationService.validate(
                yaml, EnvironmentNameParser.toApiName(environment.getName()), Set.of());
        if (!result.isValid()) {
            throw ApiException.badRequest("Invalid configuration: " + String.join("; ", result.getErrors()));
        }

        ConfigurationRevision revision = ConfigurationRevision.builder()
                .project(project)
                .environment(environment)
                .sourceCommitSha(commitSha == null || commitSha.isBlank() ? "unknown" : commitSha)
                .checksum(result.getChecksum())
                .authorIdentity(authorIdentity == null ? "pipeline" : authorIdentity)
                .configYaml(yaml)
                .validationStatus(ValidationStatus.VALID)
                .approvalStatus(RevisionApprovalStatus.PENDING)
                .artifactDigest(artifactDigest)
                .build();

        List<ConfigurationVariable> vars = new ArrayList<>();
        result.getVariables().forEach((name, value) -> vars.add(ConfigurationVariable.builder()
                .revision(revision).varName(name).varValue(value).secretRef(false).build()));
        result.getSecretReferences().forEach((name, ref) -> vars.add(ConfigurationVariable.builder()
                .revision(revision).varName(name).varValue(null).secretRef(true).secretReference(ref).build()));
        revision.setVariables(vars);
        revisionRepository.save(revision);

        auditService.record(project.getId(), null, environment.getId(),
                "CONFIG_VALIDATED", "ConfigurationRevision", revision.getId().toString(),
                Map.of("checksum", revision.getChecksum(), "source", "pipeline"));

        return ValidationReport.builder()
                .valid(true)
                .revisionId(revision.getId().toString())
                .checksum(revision.getChecksum())
                .environment(EnvironmentNameParser.toApiName(environment.getName()))
                .errors(List.of())
                .warnings(result.getWarnings())
                .secretAvailability(result.getSecretAvailability())
                .revision(toView(revision))
                .build();
    }

    @Transactional(readOnly = true)
    public RevisionView getRevision(UUID projectId, UUID revisionId, UserPrincipal principal) {
        projectService.requireAccess(projectId, principal);
        ConfigurationRevision revision = revisionRepository.findById(revisionId)
                .orElseThrow(() -> ApiException.notFound("Revision not found"));
        if (!revision.getProject().getId().equals(projectId)) {
            throw ApiException.notFound("Revision not found");
        }
        return toView(revision);
    }

    @Transactional(readOnly = true)
    public List<RevisionView> listRevisions(UUID projectId, String environment, UserPrincipal principal) {
        projectService.requireAccess(projectId, principal);
        EnvironmentName envName = EnvironmentNameParser.parse(environment);
        Environment env = environmentRepository.findByProjectIdAndName(projectId, envName)
                .orElseThrow(() -> ApiException.notFound("Environment not found"));
        return revisionRepository.findByProjectIdAndEnvironmentIdOrderByCreatedAtDesc(projectId, env.getId())
                .stream().map(this::toView).toList();
    }

    public RevisionView toView(ConfigurationRevision revision) {
        List<VariableView> vars = revision.getVariables().stream()
                .map(v -> VariableView.builder()
                        .name(v.getVarName())
                        .value(v.isSecretRef() ? null : v.getVarValue())
                        .secretRef(v.isSecretRef())
                        .secretReference(v.getSecretReference())
                        .secretVersion(v.getSecretVersion())
                        .build())
                .toList();
        return RevisionView.builder()
                .id(revision.getId().toString())
                .projectId(revision.getProject().getId().toString())
                .environmentId(revision.getEnvironment().getId().toString())
                .environment(EnvironmentNameParser.toApiName(revision.getEnvironment().getName()))
                .sourceCommitSha(revision.getSourceCommitSha())
                .checksum(revision.getChecksum())
                .authorIdentity(revision.getAuthorIdentity())
                .validationStatus(revision.getValidationStatus().name())
                .approvalStatus(revision.getApprovalStatus().name())
                .artifactDigest(revision.getArtifactDigest())
                .createdAt(revision.getCreatedAt() != null ? revision.getCreatedAt().toString() : null)
                .variables(vars)
                .build();
    }
}
