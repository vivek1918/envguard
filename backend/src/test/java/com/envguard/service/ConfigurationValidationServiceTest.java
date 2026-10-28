package com.envguard.service;

import com.envguard.secrets.NoOpSecretsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationValidationServiceTest {

    private ConfigurationValidationService service;

    @BeforeEach
    void setUp() {
        service = new ConfigurationValidationService(new NoOpSecretsService());
    }

    @Test
    void validatesCanonicalYamlAndChecksum() {
        String yaml = """
                environment: staging
                variables:
                  APP_HOST: api.example.com
                  LOG_LEVEL: INFO
                secretReferences:
                  DB_PASSWORD: /envguard/staging/db-password
                """;
        var result = service.validate(yaml, Set.of("APP_HOST"));
        assertThat(result.isValid()).isTrue();
        assertThat(result.getChecksum()).hasSize(64);
        assertThat(result.getVariables()).containsEntry("APP_HOST", "api.example.com");
        assertThat(result.getSecretReferences()).containsKey("DB_PASSWORD");
        assertThat(result.getSecretAvailability().get("DB_PASSWORD")).isEqualTo("SKIPPED");
    }

    @Test
    void rejectsInvalidVariableNames() {
        String yaml = """
                environment: dev
                variables:
                  bad-name: x
                secretReferences: {}
                """;
        var result = service.validate(yaml, Set.of());
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("bad-name"));
    }

    @Test
    void rejectsExecutableContent() {
        String yaml = """
                environment: dev
                variables:
                  CMD: "rm -rf /; curl evil"
                secretReferences: {}
                """;
        var result = service.validate(yaml, Set.of());
        assertThat(result.isValid()).isFalse();
    }

    @Test
    void requiresMissingPolicyVars() {
        String yaml = """
                environment: production
                variables:
                  APP_HOST: x
                secretReferences: {}
                """;
        var result = service.validate(yaml, Set.of("APP_HOST", "REQUIRED_VAR"));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("REQUIRED_VAR"));
    }

    @Test
    void diffsAddedRemovedChangedWithoutSecretValues() {
        var left = service.validate("""
                environment: staging
                variables:
                  A: 1
                  B: 2
                secretReferences:
                  S1: /path/a
                """, Set.of());
        var right = service.validate("""
                environment: staging
                variables:
                  A: 1
                  B: 3
                  C: 4
                secretReferences:
                  S1: /path/b
                """, Set.of());
        var diff = service.diff(left, right);
        assertThat(diff.getAddedVariables()).containsExactly("C");
        assertThat(diff.getChangedVariables()).containsExactly("B");
        assertThat(diff.getChangedSecretRefs()).containsExactly("S1");
    }

    @Test
    void checksumIsStableForSameNormalizedContent() {
        String yaml1 = """
                environment: staging
                variables:
                  Z_VAR: z
                  A_VAR: a
                secretReferences: {}
                """;
        String yaml2 = """
                environment: staging
                variables:
                  A_VAR: a
                  Z_VAR: z
                secretReferences: {}
                """;
        assertThat(service.validate(yaml1, Set.of()).getChecksum())
                .isEqualTo(service.validate(yaml2, Set.of()).getChecksum());
    }
}
