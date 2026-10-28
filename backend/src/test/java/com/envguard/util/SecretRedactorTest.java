package com.envguard.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRedactorTest {

    @Test
    void redactsInlineSecrets() {
        String input = "password=supersecret token=abc123 api_key=xyz";
        String out = SecretRedactor.redact(input);
        assertThat(out).doesNotContain("supersecret");
        assertThat(out).doesNotContain("abc123");
        assertThat(out).contains(SecretRedactor.REDACTED);
    }

    @Test
    void redactsBearerTokens() {
        String out = SecretRedactor.redact("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abc");
        assertThat(out).doesNotContain("eyJhbGciOiJIUzI1NiJ9.abc");
        assertThat(out).contains(SecretRedactor.REDACTED);
    }

    @Test
    void redactsSensitiveMapKeys() {
        Map<String, Object> redacted = SecretRedactor.redactMap(Map.of(
                "username", "alice",
                "password", "hunter2",
                "DB_SECRET", "s3cret"
        ));
        assertThat(redacted.get("username")).isEqualTo("alice");
        assertThat(redacted.get("password")).isEqualTo(SecretRedactor.REDACTED);
        assertThat(redacted.get("DB_SECRET")).isEqualTo(SecretRedactor.REDACTED);
    }

    @Test
    void leavesNullAndBlankAlone() {
        assertThat(SecretRedactor.redact(null)).isNull();
        assertThat(SecretRedactor.redact("")).isEmpty();
    }
}
