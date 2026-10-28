package com.envguard.service;

import com.envguard.domain.entity.Environment;
import com.envguard.domain.entity.Project;
import com.envguard.domain.entity.ProjectMember;
import com.envguard.domain.entity.User;
import com.envguard.domain.enums.ProjectMemberRole;
import com.envguard.domain.enums.RoleName;
import com.envguard.dto.request.CreateEnvironmentRequest;
import com.envguard.dto.request.UpdateEnvironmentRequest;
import com.envguard.exception.ApiException;
import com.envguard.repository.EnvironmentRepository;
import com.envguard.repository.ProjectMemberRepository;
import com.envguard.repository.UserRepository;
import com.envguard.security.UserPrincipal;
import com.envguard.util.EnvironmentNameParser;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectService projectService;
    private final AuditService auditService;

    @Data
    @Builder
    public static class EnvironmentView {
        private String id;
        private String projectId;
        private String name;
        private boolean requiresSecondApprover;
        private int approvalTimeoutMinutes;
        private List<String> approverUserIds;
    }

    @Transactional(readOnly = true)
    public List<EnvironmentView> list(UUID projectId, UserPrincipal principal) {
        projectService.requireAccess(projectId, principal);
        return environmentRepository.findByProjectId(projectId).stream().map(this::toView).toList();
    }

    @Transactional
    public EnvironmentView create(UUID projectId, CreateEnvironmentRequest request, UserPrincipal principal) {
        Project project = projectService.requireAccess(projectId, principal);
        requireOwnerOrAdmin(projectId, principal);

        if (environmentRepository.findByProjectIdAndName(projectId, request.getName()).isPresent()) {
            throw ApiException.conflict("Environment already exists: " + request.getName());
        }

        boolean second = request.getRequiresSecondApprover() != null
                ? request.getRequiresSecondApprover()
                : request.getName().name().equals("PRODUCTION");
        int timeout = request.getApprovalTimeoutMinutes() != null
                ? request.getApprovalTimeoutMinutes()
                : (second ? 120 : 60);

        Environment env = Environment.builder()
                .project(project)
                .name(request.getName())
                .requiresSecondApprover(second)
                .approvalTimeoutMinutes(timeout)
                .build();
        environmentRepository.save(env);
        auditService.record(projectId, principal.getId(), env.getId(),
                "ENVIRONMENT_CREATED", "Environment", env.getId().toString(),
                Map.of("name", env.getName().name()));
        return toView(env);
    }

    @Transactional
    public EnvironmentView updateApprovers(UUID projectId, UUID envId, UpdateEnvironmentRequest request,
                                           UserPrincipal principal) {
        Environment env = environmentRepository.findById(envId)
                .orElseThrow(() -> ApiException.notFound("Environment not found"));
        if (!env.getProject().getId().equals(projectId)) {
            throw ApiException.notFound("Environment not found");
        }
        projectService.requireAccess(projectId, principal);
        requireOwnerOrAdmin(projectId, principal);

        if (request.getRequiresSecondApprover() != null) {
            env.setRequiresSecondApprover(request.getRequiresSecondApprover());
        }
        if (request.getApprovalTimeoutMinutes() != null) {
            env.setApprovalTimeoutMinutes(request.getApprovalTimeoutMinutes());
        }
        if (request.getApproverUserIds() != null) {
            Set<User> approvers = request.getApproverUserIds().stream()
                    .map(id -> userRepository.findById(id)
                            .orElseThrow(() -> ApiException.notFound("Approver user not found: " + id)))
                    .collect(Collectors.toSet());
            env.setApprovers(approvers);
        }
        environmentRepository.save(env);
        auditService.record(projectId, principal.getId(), env.getId(),
                "ENVIRONMENT_APPROVERS_UPDATED", "Environment", env.getId().toString(), Map.of());
        return toView(env);
    }

    @Transactional
    public EnvironmentView update(UUID environmentId, UpdateEnvironmentRequest request, UserPrincipal principal) {
        Environment env = environmentRepository.findById(environmentId)
                .orElseThrow(() -> ApiException.notFound("Environment not found"));
        return updateApprovers(env.getProject().getId(), environmentId, request, principal);
    }

    private void requireOwnerOrAdmin(UUID projectId, UserPrincipal principal) {
        if (principal.hasRole(RoleName.ADMINISTRATOR)) {
            return;
        }
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, principal.getId())
                .orElseThrow(() -> ApiException.forbidden("Not a project member"));
        if (member.getRole() != ProjectMemberRole.OWNER && member.getRole() != ProjectMemberRole.MAINTAINER) {
            throw ApiException.forbidden("Only project owner/maintainer or administrator can manage environments");
        }
    }

    public EnvironmentView toView(Environment env) {
        return EnvironmentView.builder()
                .id(env.getId().toString())
                .projectId(env.getProject().getId().toString())
                .name(EnvironmentNameParser.toApiName(env.getName()))
                .requiresSecondApprover(env.isRequiresSecondApprover())
                .approvalTimeoutMinutes(env.getApprovalTimeoutMinutes())
                .approverUserIds(env.getApprovers().stream().map(u -> u.getId().toString()).toList())
                .build();
    }
}
