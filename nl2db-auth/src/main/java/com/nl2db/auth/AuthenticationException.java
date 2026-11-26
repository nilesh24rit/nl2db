package com.nl2db.auth;

/**
 * Exception thrown when authentication fails.
 * <p>
 * Uses a generic error message that does not reveal whether an email
 * address exists, what the account status is, or any internal
 * authentication/authorization details.
 * <p>
 * All authentication failure paths map to this exception with the same
 * generic message to prevent user enumeration and information leakage.
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }
}