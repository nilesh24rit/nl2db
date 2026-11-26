package com.nl2db.auth;

import java.time.Instant;

/**
 * Response DTO for user login.
 * <p>
 * Contains the JWT access token and refresh token with expiration metadata.
 * Does not expose passwords, password hashes, or internal credential records.
 * The refresh token is returned only once in the login response;
 * subsequent sessions obtain new tokens via the refresh endpoint.
 */
public record LoginResponse(

        String accessToken,

        String tokenType,

        String refreshToken,

        long expiresIn,

        Instant expiresAt
) {
}