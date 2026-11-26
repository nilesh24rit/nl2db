package com.nl2db.gateway.auth;

/**
 * Request DTO for email verification.
 * <p>
 * Contains the raw verification token submitted by the user.
 * The token is hashed server-side and used to look up the verification record.
 */
public record VerificationRequest(

        @NotBlank(message = "Verification token must not be blank")
        @Size(max = 2000, message = "Verification token must not exceed maximum length")
        String token
) {
}