package com.envguard.provider;

import org.springframework.stereotype.Component;

/**
 * Bitbucket Cloud / Server webhook adapter.
 *
 * <p>TODO design notes:
 * <ul>
 *   <li>Bitbucket Cloud uses HMAC SHA256 with header {@code X-Hub-Signature} (sha256=&lt;hex&gt;)
 *       when a webhook secret is configured — similar to GitHub but verify Cloud vs Server differences.</li>
 *   <li>Bitbucket Server/DC historically used {@code X-Hub-Signature} with HMAC SHA256 of raw body.</li>
 *   <li>Delivery idempotency: prefer {@code X-Request-UUID} or {@code X-Event-Key}+commit hash.</li>
 *   <li>Push payload shape differs: {@code push.changes[].new.target.hash}, {@code repository.full_name}.</li>
 *   <li>Do not log payload bodies — they may contain commit messages with accidental secrets.</li>
 * </ul>
 *
 * <p>This stub returns false / throws until product enables Bitbucket integrations.
 */
@Component
public class BitbucketRepositoryProvider implements RepositoryProvider {

    @Override
    public String getProviderName() {
        return "BITBUCKET";
    }

    @Override
    public boolean validateWebhook(String signatureHeader, String payload, String secret) {
        // TODO: implement Bitbucket HMAC validation (see class javadoc).
        return false;
    }

    @Override
    public ParsedWebhookEvent parseWebhook(String payload) {
        // TODO: map Bitbucket push JSON to ParsedWebhookEvent.
        throw new UnsupportedOperationException(
                "Bitbucket webhook parsing is not implemented yet — see BitbucketRepositoryProvider design comments");
    }
}
