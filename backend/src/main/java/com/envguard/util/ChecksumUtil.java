package com.envguard.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

public final class ChecksumUtil {

    private ChecksumUtil() {
    }

    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Canonical checksum of normalized config: sorted variable keys + secret ref keys.
     */
    public static String canonicalConfigChecksum(
            String environment,
            Map<String, String> variables,
            Map<String, String> secretReferences) {

        StringBuilder sb = new StringBuilder();
        sb.append("environment=").append(environment == null ? "" : environment.trim().toLowerCase()).append('\n');
        sb.append("variables:\n");
        new TreeMap<>(variables == null ? Map.of() : variables)
                .forEach((k, v) -> sb.append(k).append('=').append(v == null ? "" : v).append('\n'));
        sb.append("secretReferences:\n");
        new TreeMap<>(secretReferences == null ? Map.of() : secretReferences)
                .forEach((k, v) -> sb.append(k).append('=').append(v == null ? "" : v).append('\n'));
        return sha256Hex(sb.toString());
    }
}
