package com.envguard.controller;

import com.envguard.dto.request.ApprovalActionRequest;
import com.envguard.dto.response.ApiResponse;
import com.envguard.security.UserPrincipal;
import com.envguard.service.DeploymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    @GetMapping("/pending-approvals")
    public ApiResponse<List<DeploymentService.DeploymentView>> pending(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String environment,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(deploymentService.pendingApprovals(principal, projectId, environment));
    }

    @GetMapping("/{id}")
    public ApiResponse<DeploymentService.DeploymentView> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(deploymentService.get(id, principal));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<DeploymentService.DeploymentView> approve(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(deploymentService.approve(id, request, principal));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<DeploymentService.DeploymentView> reject(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(deploymentService.reject(id, request, principal));
    }

    @GetMapping("/{id}/audit")
    public ApiResponse<List<Map<String, Object>>> audit(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(deploymentService.auditTrail(id, principal));
    }
}
