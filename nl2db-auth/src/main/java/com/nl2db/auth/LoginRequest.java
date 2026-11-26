package com.nl2db.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for user login.
 * <p>
 * Contains the email and password for authentication.
 * Validation constraints are applied by Spring when {@link jakarta.validation.Valid}
 * is used on the controller parameter.
 * The {@link EmailAddress} value object enforces format rules at the domain level.
 * <p>
 * Invariants:
 * - Email must be a valid email address
 * - Password must not be blank
 * - Password is verified against the stored Argon2id hash; never sent unhashed
 */
public record LoginRequest(

        @NotBlank(message = "Email must not be blank")
        String email,

        @NotBlank(message = "Password must not be blank")
        String password
) {
}