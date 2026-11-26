package com.nl2db.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for user registration.
 * <p>
 * Contains only public-facing user identity information. Does not expose
 * credentials, password data, tokens, or internal database metadata.
 */
public record RegistrationResponse(

        UUID userId,
        String normalizedEmail,
        UserStatus status,
        Instant createdAt
) {

    /**
     * Creates a registration response from a User aggregate.
     *
     * @param user the newly created user
     */
    public static RegistrationResponse fromUser(User user) {
        return new RegistrationResponse(
                user.id(),
                user.normalizedEmail(),
                user.status(),
                user.createdAt()
        );
    }
}