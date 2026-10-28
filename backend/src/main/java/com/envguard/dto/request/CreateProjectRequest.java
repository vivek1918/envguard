package com.envguard.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProjectRequest {
    @NotBlank
    @Size(max = 128)
    private String name;

    @Size(max = 1024)
    private String description;

    @Size(max = 128)
    private String defaultBranch = "main";
}
