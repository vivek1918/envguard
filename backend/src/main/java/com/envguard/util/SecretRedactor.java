package com.envguard.util;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Redacts secret values from logs, API responses, and audit payloads.
 * Never returns or logs actual secret material.
 */
public final class SecretRedactor {

    public static final String REDACTED = "***REDACTED***";

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|secret|token|apikey|api_key|private[_-]?key|credential|authorization).*");

    private static final Pattern INLINE_SECRET = Pattern.compile(
            "(?i)((?:password|secret|token|api[_-]?key|authorization)\\s*[=:]\\s*)([^\\s,;\"']+)",
            Pattern.MULTILINE);

    private static final Pattern AWS_KEY = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*");

    private SecretRedactor() {
    }

    public static String redact(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        // Bearer tokens first — Authorization headers would otherwise only redact the word "Bearer".
        String result = BEARER.matcher(input).replaceAll("$1" + REDACTED);
        result = INLINE_SECRET.matcher(result).replaceAll("$1" + REDACTED);
        result = AWS_KEY.matcher(result).replaceAll(REDACTED);
        return result;
    }

    public static boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEY.matcher(key).matches();
    }

    public static Object redactValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return REDACTED;
        }
        if (value instanceof String s) {
            return redact(s);
        }
        return value;
    }

    public static Map<String, Object> redactMap(Map<String, ?> source) {
        if (source == null) {
            return Map.of();
        }
        return source.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> redactValue(e.getKey(), e.getValue()),
                        (a, b) -> b,
                        java.util.LinkedHashMap::new
                ));
    }

    public static String maskSecretReference(String ref) {
        if (ref == null) {
            return null;
        }
        // References (ARN/name paths) are safe to show; values never are.
        return ref;
    }

    public static String safeLower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
