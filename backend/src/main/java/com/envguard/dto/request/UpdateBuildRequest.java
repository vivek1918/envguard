package com.envguard.dto.request;

import com.envguard.domain.enums.BuildStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateBuildRequest {
    @NotNull
    private BuildStatus status;

    private String artifactDigest;
    private String artifactUrl;
    private String logs;
}
