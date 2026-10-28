package com.envguard.service;

import com.envguard.domain.entity.Environment;
import com.envguard.domain.entity.Project;
import com.envguard.domain.entity.ProjectMember;
import com.envguard.domain.entity.User;
import com.envguard.domain.enums.EnvironmentName;
import com.envguard.domain.enums.ProjectMemberRole;
import com.envguard.dto.request.AddMemberRequest;
import com.envguard.dto.request.CreateProjectRequest;
import com.envguard.dto.request.UpdateProjectRequest;
import com.envguard.exception.ApiException;
import com.envguard.repository.EnvironmentRepository;
import com.envguard.repository.ProjectMemberRepository;
import com.envguard.repository.ProjectRepository;
import com.envguard.repository.UserRepository;
import com.envguard.security.UserPrincipal;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final EnvironmentRepository environmentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Data
    @Builder
    public static class ProjectView {
        private String id;
        private String name;
        private String description;
        private String ownerId;
        private String defaultBranch;
        private List<MemberView> members;
    }

    @Data
    @Builder
    public static class MemberView {
        private String userId;
        private String username;
        private String role;
    }

    @Transactional
    public ProjectView create(CreateProjectRequest request, UserPrincipal principal) {
        if (projectRepository.existsByName(request.getName())) {
            throw ApiException.conflict("Project name already exists");
        }
        User owner = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(owner)
                .defaultBranch(request.getDefaultBranch() == null ? "main" : request.getDefaultBranch())
                .build();
        projectRepository.save(project);

        ProjectMember member = ProjectMember.builder()
                .projectId(project.getId())
                .userId(owner.getId())
                .role(ProjectMemberRole.OWNER)
                .build();
        projectMemberRepository.save(member);

        for (EnvironmentName envName : EnvironmentName.values()) {
            Environment env = Environment.builder()
                    .project(project)
                    .name(envName)
                    .requiresSecondApprover(envName == EnvironmentName.PRODUCTION)
                    .approvalTimeoutMinutes(envName == EnvironmentName.PRODUCTION ? 120 : 60)
                    .build();
            environmentRepository.save(env);
        }

        auditService.record(project.getId(), principal.getId(), null, "PROJECT_CREATED", "Project",
                project.getId().toString(), Map.of("name", project.getName()));
        return toView(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(UserPrincipal principal) {
        return projectRepository.findAll().stream()
                .filter(p -> canAccess(p.getId(), principal))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectView get(UUID id, UserPrincipal principal) {
        Project project = requireAccess(id, principal);
        return toView(project);
    }

    @Transactional
    public ProjectView update(UUID id, UpdateProjectRequest request, UserPrincipal principal) {
        Project project = requireAccess(id, principal);
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getDefaultBranch() != null) {
            project.setDefaultBranch(request.getDefaultBranch());
        }
        projectRepository.save(project);
        auditService.record(id, principal.getId(), null, "PROJECT_UPDATED", "Project", id.toString(), Map.of());
        return toView(project);
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Project project = requireAccess(id, principal);
        if (!project.getOwner().getId().equals(principal.getId()) && !principal.hasRole(com.envguard.domain.enums.RoleName.ADMINISTRATOR)) {
            throw ApiException.forbidden("Only owner or administrator can delete project");
        }
        projectRepository.delete(project);
        auditService.record(id, principal.getId(), null, "PROJECT_DELETED", "Project", id.toString(), Map.of());
    }

    @Transactional
    public ProjectView addMember(UUID projectId, AddMemberRequest request, UserPrincipal principal) {
        requireAccess(projectId, principal);
        if (!userRepository.existsById(request.getUserId())) {
            throw ApiException.notFound("User not found");
        }
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, request.getUserId())) {
            throw ApiException.conflict("User already a member");
        }
        projectMemberRepository.save(ProjectMember.builder()
                .projectId(projectId)
                .userId(request.getUserId())
                .role(request.getRole())
                .build());
        auditService.record(projectId, principal.getId(), null, "PROJECT_MEMBER_ADDED", "Project",
                projectId.toString(), Map.of("userId", request.getUserId().toString(), "role", request.getRole().name()));
        return toView(projectRepository.findById(projectId).orElseThrow());
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId, UserPrincipal principal) {
        requireAccess(projectId, principal);
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
        auditService.record(projectId, principal.getId(), null, "PROJECT_MEMBER_REMOVED", "Project",
                projectId.toString(), Map.of("userId", userId.toString()));
    }

    public boolean canAccess(UUID projectId, UserPrincipal principal) {
        if (principal.hasRole(com.envguard.domain.enums.RoleName.ADMINISTRATOR)) {
            return true;
        }
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, principal.getId());
    }

    public Project requireAccess(UUID projectId, UserPrincipal principal) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        if (!canAccess(projectId, principal)) {
            throw ApiException.forbidden("Not a project member");
        }
        return project;
    }

    private ProjectView toView(Project project) {
        List<MemberView> members = projectMemberRepository.findByProjectId(project.getId()).stream()
                .map(m -> {
                    User u = userRepository.findById(m.getUserId()).orElse(null);
                    return MemberView.builder()
                            .userId(m.getUserId().toString())
                            .username(u != null ? u.getUsername() : null)
                            .role(m.getRole().name())
                            .build();
                }).toList();
        return ProjectView.builder()
                .id(project.getId().toString())
                .name(project.getName())
                .description(project.getDescription())
                .ownerId(project.getOwner().getId().toString())
                .defaultBranch(project.getDefaultBranch())
                .members(members)
                .build();
    }
}
