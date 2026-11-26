package com.nl2db.auth;

/**
 * Exception thrown when verification token validation fails.
 * <p>
 * All verification failure paths map to this exception with a generic
 * message to prevent user enumeration and information leakage.
 * Invalid, expired, consumed, and revoked tokens all produce the same
 * generic failure response.
 */
public class VerificationException extends RuntimeException {

    public VerificationException(String message) {
        super(message);
    }
}