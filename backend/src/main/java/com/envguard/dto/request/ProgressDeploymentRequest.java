package com.envguard.dto.request;

import com.envguard.domain.enums.DeploymentRecordStatus;
import lombok.Data;

@Data
public class ProgressDeploymentRequest {
    private DeploymentRecordStatus status;
    private String healthStatus;
    private String targetHost;
    private String errorMessage;
}
