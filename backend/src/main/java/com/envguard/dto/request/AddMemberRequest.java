package com.envguard.dto.request;

import com.envguard.domain.enums.ProjectMemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddMemberRequest {
    @NotNull
    private UUID userId;

    @NotNull
    private ProjectMemberRole role;
}
