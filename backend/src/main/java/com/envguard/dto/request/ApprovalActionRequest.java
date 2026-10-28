package com.envguard.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApprovalActionRequest {
    /** Must match the revision checksum bound to the deployment request (required for approve). */
    private String checksum;

    /** Must match the build artifact digest (required for approve). */
    private String artifactDigest;

    @Size(max = 1024)
    private String comment;
}
