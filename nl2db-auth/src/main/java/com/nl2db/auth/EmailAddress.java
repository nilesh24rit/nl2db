package com.nl2db.auth;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Email address value object.
 * <p>
 * Invariants:
 * - Must not be blank
 * - Must match RFC 5322 email syntax (simplified)
 * - Normalized form uses lowercase domain part only;
 *   the local part is preserved as-is because mail providers
 *   do not universally agree on local-part case handling.
 * - Equality and hashCode are based on the normalized form
 *   so that two "the same" email addresses compare equal.
 * - The normalized form is stored immutably.
 */
public final class EmailAddress {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String normalized;
    private final String raw;

    public EmailAddress(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Email address must not be null");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Email address must not be blank");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Invalid email syntax: " + trimmed);
        }
        this.raw = trimmed;

        // Normalization policy: lowercase the domain part only.
        // The local part is preserved as-is because mail providers
        // do not universally agree on local-part case normalization.
        // Example: "User@EXAMPLE.com" -> normalized "User@example.com"
        int atIndex = trimmed.indexOf('@');
        String localPart = trimmed.substring(0, atIndex);
        String domainPart = trimmed.substring(atIndex + 1).toLowerCase();
        this.normalized = localPart + "@" + domainPart;
    }

    /**
     * Returns the raw email address as provided (trimmed but otherwise unchanged).
     */
    public String raw() {
        return raw;
    }

    /**
     * Returns the normalized form of this email address.
     * The domain part is lowercased; the local part is preserved.
     */
    public String normalized() {
        return normalized;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmailAddress that = (EmailAddress) o;
        return normalized.equals(that.normalized);
    }

    @Override
    public int hashCode() {
        return normalized.hashCode();
    }
}