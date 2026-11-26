package com.nl2db.gateway.auth;

/**
 * Response DTO for the /me endpoint, returning the authenticated
 * API key principal's identifiers.
 * No secrets, hashes, or internal IDs are exposed.
 */
public record ApiKeyMeResponse(
        UUID apiKeyId,
        UUID organizationId,
        String authType,
        java.util.List<String> authorities
) {
    public ApiKeyMeResponse {
        if (authType == null) {
            throw new IllegalArgumentException("Auth type must not be null");
        }
    }
}