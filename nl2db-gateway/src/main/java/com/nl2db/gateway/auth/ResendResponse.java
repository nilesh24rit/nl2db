package com.nl2db.gateway.auth;

/**
 * Response DTO for verification resend.
 * <p>
 * Contains only a success flag. Does not expose internal persistence details.
 * The response is intentionally generic - the same format is used for
 * existing and unknown email addresses to avoid revealing whether an
 * email address is registered.
 */
public record ResendResponse(

        boolean success
) {
}