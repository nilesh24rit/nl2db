package com.nl2db.common;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Utility class for secure API key generation.
 * <p>
 * Generates keys using a cryptographically secure random number generator.
 * Key format: nl2db_live_<public-prefix>.<secret>
 * - 256 bits (32 bytes) of random secret material
 * - Public prefix allows key identification without revealing the secret
 * - URL-safe base64 encoding of the secret
 * - Never generates keys using timestamps, UUIDs alone, predictable counters,
 *   or ordinary random functions.
 * <p>
 * The secret itself is never exposed in logs, responses (except the one-time
 * creation response), or persistence. Only a SHA-256 hash of the secret is stored.
 */
public final class ApiKeyUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int SECRET_SIZE_BYTES = 32; // 256 bits

    private ApiKeyUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Generates a new API key with the format: nl2db_live_<public-prefix>.<secret>
     * The public prefix is the first 8 bytes (hex-encoded) of the secret,
     * allowing identification without revealing the full secret.
     * The secret is 32 bytes (256 bits) URL-safe base64-encoded.
     *
     * @return the generated API key string
     */
    public static String generateApiKey() {
        // Generate 32 bytes (256 bits) of cryptographically secure random secret
        byte[] secret = new byte[SECRET_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(secret);

        // Take first 8 bytes as public prefix (hex encoded = 16 chars)
        byte[] prefixBytes = new byte[8];
        System.arraycopy(secret, 0, prefixBytes, 0, 8);
        String publicPrefix = bytesToHex(prefixBytes);

        // URL-safe base64 encode the full secret (without padding)
        String secretB64 = base64UrlEncode(secret);

        // Key format: nl2db_live_<public-prefix>.<secret>
        return "nl2db_live_" + publicPrefix + "." + secretB64;
    }

    /**
     * Parses an API key into its prefix and secret components.
     *
     * @param apiKey the API key string, or null
     * @return an array where [0] = public prefix, [1] = secret, or null if invalid
     */
    public static String[] parseApiKey(String apiKey) {
        if (apiKey == null || !apiKey.startsWith("nl2db_live_")) {
            return null;
        }

        int firstDot = apiKey.indexOf('.');
        if (firstDot < 17) { // "nl2db_live_" is 13 chars, plus at least 1 char for prefix
            return null;
        }

        String prefix = apiKey.substring(13, firstDot);
        String secret = apiKey.substring(firstDot + 1);

        return new String[]{prefix, secret};
    }

    /**
     * Returns the public prefix from an API key (the part after nl2db_live_ and before the dot).
     * This allows identifying which key was used without revealing the secret.
     *
     * @param apiKey the API key string
     * @return the public prefix, or null if invalid
     */
    public static String getPublicPrefix(String apiKey) {
        String[] parts = parseApiKey(apiKey);
        if (parts == null) return null;
        return parts[0];
    }

    /**
     * Gets the secret hash (SHA-256) of the API key secret.
     * The secret itself is never stored or exposed - only its hash is stored.
     *
     * @param apiKey the API key string
     * @return the SHA-256 hash of the secret, or null if invalid
     */
    public static String getSecretHash(String apiKey) {
        String[] parts = parseApiKey(apiKey);
        if (parts == null) return null;

        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(parts[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a key identifier (UUID) for the API key.
     *
     * @return a unique key identifier
     */
    public static UUID createKeyId() {
        return new UUID(SECURE_RANDOM.nextLong(), SECURE_RANDOM.nextLong());
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String base64UrlEncode(byte[] bytes) {
        // Standard base64 without padding, then replace + with - and / with _
        String base64 = Base64.getEncoder().withoutPadding().encodeToString(bytes);
        return base64.replace('+', '-').replace('/', '_');
    }
}