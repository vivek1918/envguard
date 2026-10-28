package com.envguard.controller;

import com.envguard.dto.request.*;
import com.envguard.dto.response.ApiResponse;
import com.envguard.security.UserPrincipal;
import com.envguard.service.ConfigurationService;
import com.envguard.service.EnvironmentService;
import com.envguard.service.IntegrationService;
import com.envguard.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final EnvironmentService environmentService;
    private final ConfigurationService configurationService;
    private final IntegrationService integrationService;

    @GetMapping
    public ApiResponse<List<ProjectService.ProjectView>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(projectService.list(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectService.ProjectView> create(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(projectService.create(request, principal));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectService.ProjectView> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(projectService.get(id, principal));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectService.ProjectView> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(projectService.update(id, request, principal));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<ProjectService.ProjectView> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(projectService.addMember(id, request, principal));
    }

    @GetMapping("/{id}/environments")
    public ApiResponse<List<EnvironmentService.EnvironmentView>> environments(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(environmentService.list(id, principal));
    }

    @PostMapping("/{id}/environments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EnvironmentService.EnvironmentView> createEnvironment(
            @PathVariable UUID id,
            @Valid @RequestBody CreateEnvironmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(environmentService.create(id, request, principal));
    }

    @PutMapping("/{id}/environments/{envId}/approvers")
    public ApiResponse<EnvironmentService.EnvironmentView> updateApprovers(
            @PathVariable UUID id,
            @PathVariable UUID envId,
            @Valid @RequestBody UpdateEnvironmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(environmentService.updateApprovers(id, envId, request, principal));
    }

    @PostMapping("/{id}/integrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IntegrationService.IntegrationView> createIntegration(
            @PathVariable UUID id,
            @Valid @RequestBody CreateIntegrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(integrationService.create(id, request, principal));
    }

    @PostMapping("/{projectId}/configurations/validate")
    public ApiResponse<ConfigurationService.ValidationReport> validate(
            @PathVariable UUID projectId,
            @Valid @RequestBody ValidateConfigRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(configurationService.validateAndStore(projectId, request, principal));
    }

    @GetMapping("/{projectId}/configurations/{revisionId}")
    public ApiResponse<ConfigurationService.RevisionView> getRevision(
            @PathVariable UUID projectId,
            @PathVariable UUID revisionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(configurationService.getRevision(projectId, revisionId, principal));
    }

    @GetMapping("/{projectId}/configurations")
    public ApiResponse<List<ConfigurationService.RevisionView>> listRevisions(
            @PathVariable UUID projectId,
            @RequestParam String environment,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(configurationService.listRevisions(projectId, environment, principal));
    }
}
