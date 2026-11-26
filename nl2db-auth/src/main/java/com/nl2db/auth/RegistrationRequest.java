package com.nl2db.auth;

/**
 * Request DTO for user registration.
 * <p>
 * Only email and password are accepted. Organization ID and account status
 * are never accepted from the caller.
 * <p>
 * The email is validated by {@link EmailAddress} at the domain level. The
 * password must be non-blank and at least eight characters long; it is
 * hashed with Argon2id before persistence.
 */
public record RegistrationRequest(String email, String password) {

    public RegistrationRequest {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
    }
}
