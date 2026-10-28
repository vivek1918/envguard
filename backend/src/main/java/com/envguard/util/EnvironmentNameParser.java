package com.envguard.util;

import com.envguard.domain.enums.EnvironmentName;
import com.envguard.exception.ApiException;

import java.util.Locale;

public final class EnvironmentNameParser {

    private EnvironmentNameParser() {
    }

    public static EnvironmentName parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Environment name is required");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "dev", "development" -> EnvironmentName.DEV;
            case "staging", "stage" -> EnvironmentName.STAGING;
            case "production", "prod" -> EnvironmentName.PRODUCTION;
            default -> {
                try {
                    yield EnvironmentName.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw ApiException.badRequest("Unknown environment: " + raw);
                }
            }
        };
    }

    public static String toApiName(EnvironmentName name) {
        return name.name().toLowerCase(Locale.ROOT);
    }
}
