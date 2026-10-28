package com.envguard.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateRevisionRequest {
    @NotNull
    private UUID projectId;

    @NotNull
    private UUID environmentId;

    @NotBlank
    private String sourceCommitSha;

    @NotBlank
    private String configYaml;

    private String artifactDigest;
}
