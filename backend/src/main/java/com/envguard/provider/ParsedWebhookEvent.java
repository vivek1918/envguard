package com.envguard.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParsedWebhookEvent {
    private String deliveryId;
    private String eventType;
    private String repoOwner;
    private String repoName;
    private String commitSha;
    private String branch;
    private String authorIdentity;
    private String externalRepoId;
}
