package com.envguard.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubRepositoryProvider implements RepositoryProvider {

    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return "GITHUB";
    }

    @Override
    public boolean validateWebhook(String signatureHeader, String payload, String secret) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=") || secret == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(digest);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("GitHub webhook signature validation failed");
            return false;
        }
    }

    @Override
    public ParsedWebhookEvent parseWebhook(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode repo = root.path("repository");
            JsonNode headCommit = root.path("head_commit");
            String ref = root.path("ref").asText("");
            String branch = ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;

            String deliveryId = root.path("delivery_id").asText(null);
            if (deliveryId == null || deliveryId.isBlank()) {
                // GitHub sends delivery id in header; fallback hash of payload for parse-only paths
                deliveryId = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(payload.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
            }

            return ParsedWebhookEvent.builder()
                    .deliveryId(deliveryId)
                    .eventType(root.path("action").asText("push"))
                    .repoOwner(repo.path("owner").path("login").asText())
                    .repoName(repo.path("name").asText())
                    .commitSha(headCommit.path("id").asText(root.path("after").asText()))
                    .branch(branch)
                    .authorIdentity(headCommit.path("author").path("username")
                            .asText(headCommit.path("author").path("name").asText("unknown")))
                    .externalRepoId(repo.path("id").asText(null))
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid GitHub webhook payload", e);
        }
    }
}
