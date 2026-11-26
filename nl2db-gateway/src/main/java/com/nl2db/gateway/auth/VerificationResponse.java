package com.nl2db.gateway.auth;

/**
 * Response DTO for email verification success.
 * <p>
 * Contains only public-facing user identity information after successful
 * verification. Does not expose internal persistence details or token hashes.
 */
public record VerificationResponse(

        UUID userId,

        String normalizedEmail,

        UserStatus status
) {
}