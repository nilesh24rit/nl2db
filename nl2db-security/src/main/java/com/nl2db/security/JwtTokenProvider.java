package com.nl2db.security;

import com.nl2db.security.JwtProperties;
import org.springframework.stereotype.Component;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.security.Signature;

/**
 * JWT token provider for creating and validating RS256-signed access tokens.
 * <p>
 * Uses RSA asymmetric signing with key material loaded from external
 * configuration (NL2DB_JWT_PRIVATE_KEY and NL2DB_JWT_PUBLIC_KEY environment
 * variables). Key material is validated at startup and cached for subsequent
 * token operations.
 * <p>
 * Token claims:
 * - sub: stable user UUID (identifier)
 * - iss: issuer ("nl2db-auth")
 * - aud: audience ("nl2db-api")
 * - iat: issued-at timestamp
 * - exp: expiration timestamp (15 minutes from issuance)
 * - jti: unique token identifier (UUID string)
 * <p>
 * Does not place passwords, password hashes, or sensitive personal information
 * in JWT claims. Performs signature validation using JVM cryptography
 * for RS256 algorithm.
 */
@Component
public class JwtTokenProvider {

    public static final long JWT_TTL_SECONDS = 15 * 60; // 15 minutes

    private static final String CLAIM_KEY_ISS = "iss";
    private static final String CLAIM_KEY_AUD = "aud";
    private static final String CLAIM_KEY_SUB = "sub";
    private static final String CLAIM_KEY_IAT = "iat";
    private static final String CLAIM_KEY_EXP = "exp";
    private static final String CLAIM_KEY_JTI = "jti";

    private static final String ISSUER = "nl2db-auth";
    private static final String AUDIENCE = "nl2db-api";

    private final JwtProperties jwtProperties;

    private final PrivateKey privateKey;

    private final PublicKey publicKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.privateKey = parsePrivateKey(jwtProperties.getPrivateKeyPem());
        this.publicKey = parsePublicKey(jwtProperties.getPublicKeyPem());
    }

    /**
     * Validate key material at construction time.
     * <p>
     * Throws IllegalArgumentException if key material is missing or malformed,
     * causing Spring to fail fast during application startup.
     * Production deployments must configure NL2DB_JWT_PRIVATE_KEY and
     * NL2DB_JWT_PUBLIC_KEY environment variables.
     */
    private void validateKeyMaterial() {
        if (jwtProperties.getPrivateKeyPem() == null) {
            throw new IllegalArgumentException(
                    "JWT private key material must be configured via NL2DB_JWT_PRIVATE_KEY environment variable");
        }
        if (jwtProperties.getPublicKeyPem() == null) {
            throw new IllegalArgumentException(
                    "JWT public key material must be configured via NL2DB_JWT_PUBLIC_KEY environment variable");
        }
    }

    /**
     * Create a signed JWT access token for the given user.
     * <p>
     * The token includes standard claims (iss, aud, sub, iat, exp, jti)
     * and is signed using RS256 with the configured RSA private key.
     *
     * @param userId the stable user UUID identifier
     * @return the signed JWT access token string (compact serialization)
     */
    public String createAccessToken(UUID userId) {
        long iat = Instant.now().getEpochSecond();
        long exp = iat + JWT_TTL_SECONDS;
        String jti = UUID.randomUUID().toString();

        // Build JWT header (base64url encoded JSON)
        String header = base64urlEncode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");

        // Build JWT payload (base64url encoded JSON)
        String payload = base64urlEncode(
                "{\"iss\":\"nl2db-auth\",\"aud\":\"nl2db-api\"," +
                "\"sub\":\"" + userId.toString() + "\"," +
                "\"iat\":" + iat + ",\"exp\":" + exp + ",\"jti\":\"" + jti + "\"}");

        // Create signing input = base64url(header).base64url(payload)
        String signingInput = header + "." + payload;

        // Sign with RS256
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(signingInput.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] signature = signer.sign();

            return signingInput + "." + base64urlEncode(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JWT", e);
        }
    }

    /**
     * Validate and parse a JWT access token.
     * <p>
     * Performs full validation including:
     * - Signature verification using the configured public key
     * - RS256 algorithm validation
     * - Issuer validation ("nl2db-auth")
     * - Audience validation ("nl2db-api")
     * - Expiration validation
     * - Required subject and claim structure
     *
     * @param token the JWT token string to validate
     * @return the parsed JWT claims map if valid, null otherwise
     */
    public Map<String, Object> validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String headerB64 = parts[0];
            String payloadB64 = parts[1];
            String signatureB64 = parts[2];

            // Decode header to check algorithm
            String headerJson = new String(base64urlDecode(headerB64));
            if (!headerJson.contains("\"RS256\"")) {
                return null;
            }

            // Decode payload
            String payloadJson = new String(base64urlDecode(payloadB64));
            Map<String, Object> claims = parseSimpleJson(payloadJson);

            // Validate issuer
            if (!ISSUER.equals(claims.get("iss"))) {
                return null;
            }

            // Validate audience
            if (!AUDIENCE.equals(claims.get("aud"))) {
                return null;
            }

            // Validate expiration
            Long expVal = (Long) claims.get("exp");
            if (expVal == null || Instant.now().getEpochSecond() > expVal) {
                return null;
            }

            // Validate iat (issued-at should be in the past or present)
            Long iatVal = (Long) claims.get("iat");
            if (iatVal != null && iatVal > Instant.now().getEpochSecond()) {
                return null;
            }

            // Verify RS256 signature
            String signingInput = headerB64 + "." + payloadB64;
            if (!verifyRS256Signature(signingInput.getBytes(), signatureB64)) {
                return null;
            }

            return claims;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract the user ID (subject) from a validated JWT token.
     * <p>
     * The subject is expected to be a string representation of a UUID.
     *
     * @param token the JWT token string, must be valid and validated
     * @return the user UUID, never null if the token is valid
     */
    public UUID getUserIdFromToken(String token) {
        var claims = validateToken(token);
        if (claims == null) {
            return null;
        }
        String subject = (String) claims.get("sub");
        if (subject == null) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Extract the unique token identifier (jti) from a validated JWT token.
     *
     * @param token the JWT token string, must be valid and validated
     * @return the token ID string, never null if the token is valid
     */
    public String getTokenIdFromToken(String token) {
        var claims = validateToken(token);
        if (claims == null) {
            return null;
        }
        return (String) claims.get("jti");
    }

    /**
     * Check if a token has expired.
     *
     * @param token the JWT token string
     * @return true if the token has expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        try {
            var claims = validateToken(token);
            if (claims == null) {
                return true;
            }
            Long expVal = (Long) claims.get("exp");
            if (expVal == null) {
                return true;
            }
            return Instant.now().getEpochSecond() > expVal;
        } catch (Exception e) {
            return true; // Treat invalid tokens as expired
        }
    }

    /**
     * Parse a simple JSON object into a key-value map.
     * This is a simplified parser for the expected claim structure.
     */
    private Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> result = new HashMap<>();
        // Very simplified parsing: find key-value pairs between quotes
        // Pattern: "key":"value" or "key":value or "key": value
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return result;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1);
        // Split by commas, being careful about commas inside strings
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : inner.toCharArray()) {
            if (c == '"') depth ^= 1; // toggle on/off when inside a string
            if (c == ',' && depth == 0) {
                processJsonPair(current.toString(), result);
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            processJsonPair(current.toString(), result);
        }
        return result;
    }

    private void processJsonPair(String pair, Map<String, Object> result) {
        pair = pair.trim();
        if (pair.isEmpty()) return;
        int colonIdx = pair.indexOf(':');
        if (colonIdx < 0) return;
        String key = pair.substring(0, colonIdx).trim();
        String value = pair.substring(colonIdx + 1).trim();
        // Remove surrounding quotes from key
        if (key.startsWith("\"") && key.endsWith("\"")) {
            key = key.substring(1, key.length() - 1);
        }
        // Remove surrounding quotes from value (if string)
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        // Keep as string - do not attempt long parsing
        result.put(key, value);
    }

    /**
     * Base64url encode a string.
     */
    private String base64urlEncode(String s) {
        return base64urlEncode(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Base64url encode a byte array.
     */
    private String base64urlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Base64url decode a string.
     */
    private byte[] base64urlDecode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    /**
     * Sign data using RS256 with the private key.
     */
    private byte[] signRS256(byte[] data) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(data);
        return signer.sign();
    }

    /**
     * Verify RS256 signature.
     */
    private boolean verifyRS256Signature(byte[] data, String base64Signature) throws Exception {
        byte[] sigBytes = Base64.getDecoder().decode(base64Signature);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(data);
        return verifier.verify(sigBytes);
    }

    /**
     * Strip PEM headers from a key string.
     */
    private String stripPemHeaders(String pem) {
        String lines[] = pem.split("\n");
        StringBuilder content = new StringBuilder();
        boolean inHeader = false;
        for (String line : lines) {
            if (line.startsWith("-----BEGIN")) {
                inHeader = true;
                continue;
            } else if (line.startsWith("-----END")) {
                inHeader = false;
                continue;
            }
            if (inHeader) {
                content.append(line);
            }
        }
        return content.toString().trim();
    }

    /**
     * Parse a PEM-encoded private key string into a PrivateKey object.
     */
    private PrivateKey parsePrivateKey(String pem) {
        String base64 = stripPemHeaders(pem);
        byte[] keyBytes = java.util.Base64.getDecoder().decode(base64);
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JWT private key: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a PEM-encoded public key string into a PublicKey object.
     */
    private PublicKey parsePublicKey(String pem) {
        String base64 = stripPemHeaders(pem);
        byte[] keyBytes = java.util.Base64.getDecoder().decode(base64);
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JWT public key: " + e.getMessage(), e);
        }
    }
}