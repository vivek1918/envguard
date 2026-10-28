package com.envguard.provider;

public interface RepositoryProvider {
    String getProviderName();

    boolean validateWebhook(String signatureHeader, String payload, String secret);

    ParsedWebhookEvent parseWebhook(String payload);
}
