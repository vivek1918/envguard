package com.envguard.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.envguard.EnvGuardApplication.class)
@ActiveProfiles("test")
@Transactional
class WebhookDuplicateTest {

    @Autowired
    private WebhookService webhookService;

    @Test
    void duplicateDeliveryIdIsIgnored() throws Exception {
        String secret = "whsec-test";
        String payload = """
                {
                  "ref": "refs/heads/main",
                  "after": "deadbeef",
                  "repository": {
                    "id": 1,
                    "name": "demo",
                    "owner": { "login": "acme" }
                  },
                  "head_commit": {
                    "id": "deadbeef",
                    "author": { "username": "alice", "name": "Alice" }
                  }
                }
                """;
        String deliveryId = "delivery-" + UUID.randomUUID();
        String signature = sign(secret, payload);

        Map<String, Object> first = webhookService.handle("GITHUB", signature, deliveryId, payload, secret);
        Map<String, Object> second = webhookService.handle("GITHUB", signature, deliveryId, payload, secret);

        assertThat(first.get("duplicate")).isEqualTo(false);
        assertThat(second.get("duplicate")).isEqualTo(true);
        assertThat(second.get("deliveryId")).isEqualTo(deliveryId);
    }

    private static String sign(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
