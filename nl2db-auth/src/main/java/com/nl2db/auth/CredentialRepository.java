package com.nl2db.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract for Credential persistence.
 * <p>
 * Owned by nl2db-auth. Provides the basic operations required by the
 * authentication workflow. Extends Spring Data JpaRepository for
 * standard CRUD operations with Credential entity.
 * <p>
 * Do not introduce unnecessary generic repository abstractions.
 * This interface stays focused on Credential-specific queries.
 */
@Repository
public interface CredentialRepository extends JpaRepository<Credential, Long> {

    /**
     * Find a credential by the associated user's ID.
     * <p>
     * Uses a native query to join with the user_id foreign key column.
     *
     * @param userId the user identifier (VARCHAR(36))
     * @return the credential, or null if not found
     */
    @Query("SELECT c FROM Credential c WHERE c.user.id = :userId")
    Optional<Credential> findByUserId(UUID userId);

    /**
     * Check whether a credential exists for the given user.
     *
     * @param userId the user identifier
     * @return true if a credential exists for this user
     */
    boolean existsByUserId(UUID userId);
}