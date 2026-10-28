package com.envguard.dto.request;

import com.envguard.domain.enums.EnvironmentName;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateEnvironmentRequest {
    @NotNull
    private EnvironmentName name;

    private Boolean requiresSecondApprover;

    @Min(1)
    private Integer approvalTimeoutMinutes;
}
