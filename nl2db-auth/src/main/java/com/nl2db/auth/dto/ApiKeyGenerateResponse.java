package com.nl2db.auth.dto;

import java.time.Instant;

/**
 * Response DTO for API key generation - returned only once at creation.
 * <p>
 * Contains the complete API key in this one-time response, along with
 * metadata. The secret itself is never exposed in any subsequent retrieval
 * or listing operations.
 * <p>
 * Invariants:
 * - keyId is the internal database identifier
 * - keyName is the human-readable name provided at creation
 * - publicPrefix allows key identification without revealing the secret
 * - completeKey is only included in this one-time creation response
 * - createdAt is the timestamp of key creation
 * - The secret is never included in this or any other response
 */
public class ApiKeyGenerateResponse {

    private final UUID keyId;
    private final String keyName;
    private final String publicPrefix;
    private final String completeKey;
    private final Instant createdAt;

    /**
     * Creates a new API key generation response.
     *
     * @param keyId the internal key identifier
     * @param keyName the human-readable key name
     * @param publicPrefix the public prefix for key identification
     * @param completeKey the complete API key (only in this one-time response)
     * @param createdAt the creation timestamp
     */
    public ApiKeyGenerateResponse(UUID keyId, String keyName, String publicPrefix,
                                  String completeKey, Instant createdAt) {
        this.keyId = keyId;
        this.keyName = keyName;
        this.publicPrefix = publicPrefix;
        this.completeKey = completeKey;
        this.createdAt = createdAt;
    }

    public UUID keyId() {
        return keyId;
    }

    public String keyName() {
        return keyName;
    }

    public String publicPrefix() {
        return publicPrefix;
    }

    /**
     * Returns the complete API key. Only valid in the creation response.
     * Subsequent retrieval or listing operations must not expose this field.
     */
    public String completeKey() {
        return completeKey;
    }

    public Instant createdAt() {
        return createdAt;
    }
}