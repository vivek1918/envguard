package com.envguard.controller;

import com.envguard.dto.request.*;
import com.envguard.dto.response.ApiResponse;
import com.envguard.service.DeploymentService;
import com.envguard.service.PipelineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;

    @PostMapping("/builds")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PipelineService.BuildView> registerBuild(@Valid @RequestBody RegisterBuildRequest request) {
        return ApiResponse.ok(pipelineService.registerOrUpdateBuild(request));
    }

    @PostMapping("/deployments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeploymentService.DeploymentView> createDeployment(
            @Valid @RequestBody CreateDeploymentRequestDto request) {
        return ApiResponse.ok(pipelineService.createDeployment(request));
    }

    @GetMapping("/deployments/{id}/status")
    public ApiResponse<DeploymentService.DeploymentView> status(@PathVariable UUID id) {
        return ApiResponse.ok(pipelineService.status(id));
    }

    @PostMapping("/deployments/{id}/verify")
    public ApiResponse<PipelineService.VerifyResult> verify(
            @PathVariable UUID id,
            @Valid @RequestBody VerifyDeploymentRequest request) {
        return ApiResponse.ok(pipelineService.verify(id, request));
    }

    @PostMapping("/deployments/{id}/progress")
    public ApiResponse<Map<String, Object>> progress(
            @PathVariable UUID id,
            @Valid @RequestBody ProgressDeploymentRequest request) {
        return ApiResponse.ok(pipelineService.progress(id, request));
    }

    @PostMapping("/deployments/{id}/complete")
    public ApiResponse<DeploymentService.DeploymentView> complete(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteDeploymentRequest request) {
        return ApiResponse.ok(pipelineService.complete(id, request));
    }
}
