package com.nl2db.gateway.auth;

/**
 * Response DTO for API key validation.
 * Contains a safe message (no secrets, hashes, or internal IDs leaked)
 * and the authenticated principal's identifiers.
 */
public record ApiKeyValidationResponse(
        String message,
        UUID apiKeyId,
        UUID organizationId,
        String errorType) {

    public ApiKeyValidationResponse {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
    }
}