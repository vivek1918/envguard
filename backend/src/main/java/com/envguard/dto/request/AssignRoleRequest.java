package com.envguard.dto.request;

import com.envguard.domain.enums.RoleName;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignRoleRequest {
    @NotNull
    private RoleName role;
}
