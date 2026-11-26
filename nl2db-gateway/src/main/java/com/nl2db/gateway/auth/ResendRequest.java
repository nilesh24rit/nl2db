package com.nl2db.gateway.auth;

/**
 * Request DTO for resending verification email.
 * <p>
 * Contains the user's email address for resend request.
 * Email is normalized using the existing EmailAddress abstraction.
 */
public record ResendRequest(

        @NotBlank(message = "Email must not be blank")
        @Size(max = 255, message = "Email must not exceed maximum length")
        String email
) {
}