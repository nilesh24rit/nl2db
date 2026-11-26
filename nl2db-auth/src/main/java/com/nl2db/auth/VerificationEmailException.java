package com.nl2db.auth;

/**
 * Exception thrown when verification email delivery fails.
 * <p>
 * This is a runtime exception that wraps the underlying messaging error.
 * When email delivery fails, the verification token is revoked so it
 * cannot be used, and the user can request a resend.
 */
public class VerificationEmailException extends RuntimeException {

    public VerificationEmailException(String message) {
        super(message);
    }
}