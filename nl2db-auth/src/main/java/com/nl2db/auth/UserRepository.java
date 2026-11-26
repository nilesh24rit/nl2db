package com.nl2db.auth;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository contract for User persistence.
 * <p>
 * Owned by nl2db-auth. Provides the basic operations required by the
 * domain at this stage. Extends Spring Data JpaRepository for
 * standard CRUD operations with User entity.
 * <p>
 * Do not introduce unnecessary generic repository abstractions.
 * This interface stays focused on User-specific queries.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by normalized email address.
     *
     * @param normalizedEmail the normalized email (lowercase domain)
     * @return the user, or null if not found
     */
    User findByNormalizedEmail(String normalizedEmail);

    /**
     * Check whether a normalized email address already exists in the database.
     *
     * @param normalizedEmail the normalized email to check
     * @return true if a user with this email already exists
     */
    boolean existsByNormalizedEmail(String normalizedEmail);
}