package com.envguard.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateEnvironmentRequest {
    private Boolean requiresSecondApprover;

    @Min(1)
    private Integer approvalTimeoutMinutes;

    private List<UUID> approverUserIds;
}
