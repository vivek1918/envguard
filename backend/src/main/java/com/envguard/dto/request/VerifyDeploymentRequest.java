package com.envguard.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyDeploymentRequest {
    @NotBlank
    private String checksum;

    @NotBlank
    private String artifactDigest;

    @NotBlank
    private String commitSha;

    @NotBlank
    private String environment;
}
