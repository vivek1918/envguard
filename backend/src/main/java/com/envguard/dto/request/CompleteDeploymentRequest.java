package com.envguard.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompleteDeploymentRequest {
    @NotNull
    private Boolean success;

    private String healthStatus;
    private String targetHost;
    private Long durationMs;
    private String errorMessage;
}
