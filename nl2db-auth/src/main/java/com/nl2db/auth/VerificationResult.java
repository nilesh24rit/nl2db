package com.nl2db.auth;

/**
 * Result of a verification token evaluation.
 */
public class VerificationResult {

    private final User user;
    private final boolean verificationSucceeded;

    public VerificationResult(User user, boolean verificationSucceeded) {
        this.user = user;
        this.verificationSucceeded = verificationSucceeded;
    }

    public User user() {
        return user;
    }

    public boolean verificationSucceeded() {
        return verificationSucceeded;
    }
}