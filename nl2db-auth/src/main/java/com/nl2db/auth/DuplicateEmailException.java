package com.nl2db.auth;

/**
 * Exception thrown when a registration attempt uses an email address
 * that is already registered in the system.
 * <p>
 * This is a domain-level exception that translates to an HTTP 409
 * Conflict response at the gateway layer. The exception message does
 * not expose whether the existing account is active, suspended, or
 * in any other state – only that the email is already in use.
 */
public class DuplicateEmailException extends RuntimeException {

    private static final String TEMPLATE = "Email address %s is already registered";

    /**
     * Constructs a duplicate email exception.
     *
     * @param normalizedEmail the normalized email address that is already registered
     */
    public DuplicateEmailException(String normalizedEmail) {
        super(String.format(TEMPLATE, normalizedEmail));
    }
}