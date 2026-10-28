package com.envguard.dto.request;

import com.envguard.domain.enums.RepoProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateIntegrationRequest {
    @NotNull
    private RepoProvider provider;

    @NotBlank
    private String repoUrl;

    @NotBlank
    private String repoOwner;

    @NotBlank
    private String repoName;

    /** Reference path only — never plaintext webhook secret. */
    private String webhookSecretRef;

    private String defaultBranch;

    private String externalRepoId;
}
