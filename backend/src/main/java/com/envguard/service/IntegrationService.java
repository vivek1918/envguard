package com.envguard.service;

import com.envguard.domain.entity.Project;
import com.envguard.domain.entity.RepositoryIntegration;
import com.envguard.domain.enums.ProjectMemberRole;
import com.envguard.domain.enums.RoleName;
import com.envguard.dto.request.CreateIntegrationRequest;
import com.envguard.exception.ApiException;
import com.envguard.repository.ProjectMemberRepository;
import com.envguard.repository.RepositoryIntegrationRepository;
import com.envguard.security.UserPrincipal;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegrationService {

    private final RepositoryIntegrationRepository integrationRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectService projectService;
    private final AuditService auditService;

    @Data
    @Builder
    public static class IntegrationView {
        private String id;
        private String projectId;
        private String provider;
        private String repoUrl;
        private String repoOwner;
        private String repoName;
        private String webhookSecretRef;
        private String defaultBranch;
        private String externalRepoId;
    }

    @Transactional
    public IntegrationView create(UUID projectId, CreateIntegrationRequest request, UserPrincipal principal) {
        Project project = projectService.requireAccess(projectId, principal);
        requireOwnerOrAdmin(projectId, principal);

        RepositoryIntegration integration = RepositoryIntegration.builder()
                .project(project)
                .provider(request.getProvider())
                .repoUrl(request.getRepoUrl())
                .repoOwner(request.getRepoOwner())
                .repoName(request.getRepoName())
                .webhookSecretRef(request.getWebhookSecretRef())
                .defaultBranch(request.getDefaultBranch() == null ? "main" : request.getDefaultBranch())
                .externalRepoId(request.getExternalRepoId())
                .build();
        integrationRepository.save(integration);

        auditService.record(projectId, principal.getId(), null, "INTEGRATION_CREATED",
                "RepositoryIntegration", integration.getId().toString(),
                Map.of("provider", request.getProvider().name(), "repo", request.getRepoOwner() + "/" + request.getRepoName()));

        return toView(integration);
    }

    @Transactional(readOnly = true)
    public List<IntegrationView> list(UUID projectId, UserPrincipal principal) {
        projectService.requireAccess(projectId, principal);
        return integrationRepository.findByProjectId(projectId).stream().map(this::toView).toList();
    }

    private void requireOwnerOrAdmin(UUID projectId, UserPrincipal principal) {
        if (principal.hasRole(RoleName.ADMINISTRATOR)) {
            return;
        }
        var member = projectMemberRepository.findByProjectIdAndUserId(projectId, principal.getId())
                .orElseThrow(() -> ApiException.forbidden("Not a project member"));
        if (member.getRole() != ProjectMemberRole.OWNER && member.getRole() != ProjectMemberRole.MAINTAINER) {
            throw ApiException.forbidden("Only owner/maintainer can manage integrations");
        }
    }

    private IntegrationView toView(RepositoryIntegration i) {
        return IntegrationView.builder()
                .id(i.getId().toString())
                .projectId(i.getProject().getId().toString())
                .provider(i.getProvider().name())
                .repoUrl(i.getRepoUrl())
                .repoOwner(i.getRepoOwner())
                .repoName(i.getRepoName())
                .webhookSecretRef(i.getWebhookSecretRef())
                .defaultBranch(i.getDefaultBranch())
                .externalRepoId(i.getExternalRepoId())
                .build();
    }
}
