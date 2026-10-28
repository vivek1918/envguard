package com.envguard.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ValidateConfigRequest {
    /** Environment name: dev | staging | production */
    @NotBlank
    private String environment;

    /** Raw YAML config. Either this or {@link #config} must be provided. */
    private String configYaml;

    /** Parsed config map alternative to YAML. */
    private Map<String, Object> config;

    private String commitSha;

    private String artifactDigest;
}
