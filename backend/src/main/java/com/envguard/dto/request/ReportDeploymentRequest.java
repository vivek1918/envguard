package com.envguard.dto.request;

import com.envguard.domain.enums.DeploymentRecordStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportDeploymentRequest {
    @NotNull
    private DeploymentRecordStatus status;

    private String targetHost;
    private String healthStatus;
    private Long durationMs;
    private String errorMessage;
    private boolean completed;
    private boolean failed;
}
