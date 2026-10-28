package com.envguard.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterBuildRequest {
    private UUID projectId;
    private String projectName;

    @NotBlank
    private String commitSha;

    @NotBlank
    private String branch;

    @NotBlank
    private String pipelineRunId;

    private String artifactDigest;
    private String artifactUrl;
    private String status;
}
