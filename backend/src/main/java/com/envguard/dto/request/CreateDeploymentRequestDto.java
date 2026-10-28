package com.envguard.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateDeploymentRequestDto {
    private UUID projectId;
    private String projectName;

    /** Environment name: DEV/STAGING/PRODUCTION (case-insensitive). */
    @NotBlank
    private String environment;

    private String configYaml;
    private Map<String, Object> config;

    private String commitSha;
    private String artifactDigest;
    private String branch;

    private UUID buildId;
    private String pipelineRunId;

    @NotBlank
    private String requesterIdentity;

    @NotBlank
    private String idempotencyKey;
}
