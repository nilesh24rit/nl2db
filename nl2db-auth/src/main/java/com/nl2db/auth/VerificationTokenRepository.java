package com.nl2db.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository contract for VerificationToken persistence.
 * <p>
 * Owned by nl2db-auth. Provides operations for verification token lifecycle
 * management including lookup by hash, consumption, and revocation.
 * <p>
 * Do not introduce unnecessary generic repository abstractions.
 * This interface stays focused on VerificationToken-specific queries and
 * atomic lifecycle operations.
 */
@Repository
public class VerificationTokenRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Find a verification token by its stored hash.
     * <p>
     * Only returns active (non-consumed, non-revoked, non-expired) tokens.
     *
     * @param tokenHash the SHA-256 hash of the verification token
     * @return the verification token, or null if not found or expired
     */
    public Optional<VerificationToken> findByTokenHash(String tokenHash) {
        return entityManager.createQuery(
                        "SELECT vt FROM VerificationToken vt " +
                                "WHERE vt.tokenHash = :tokenHash " +
                                "AND vt.consumedAt IS NULL " +
                                "AND vt.revokedAt IS NULL " +
                                "AND vt.expiresAt > CURRENT_TIMESTAMP",
                        VerificationToken.class)
                .setParameter("tokenHash", tokenHash)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Find a verification token by hash with pessimistic locking.
     * <p>
     * Uses database-level pessimistic locking to ensure exclusive access
     * for concurrent verification operations.
     * Returns null if no token with the given hash exists.
     *
     * @param tokenHash the verification token hash
     * @return the locked verification token, or null if not found
     */
    public VerificationToken findByTokenHashLocked(String tokenHash) {
        try {
            return entityManager.createQuery(
                            "SELECT vt FROM VerificationToken vt " +
                                    "WHERE vt.tokenHash = :tokenHash",
                            VerificationToken.class)
                    .setParameter("tokenHash", tokenHash)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            return null;
        }
    }

    /**
     * Find active verification tokens by user ID.
     *
     * @param userId the user identifier
     * @list of active (non-consumed, non-revoked, non-expired) verification tokens
     */
    public java.util.List<VerificationToken> findByUserId(UUID userId) {
        return entityManager.createQuery(
                        "SELECT vt FROM VerificationToken vt " +
                                "WHERE vt.userId = :userId " +
                                "AND vt.consumedAt IS NULL " +
                                "AND vt.revokedAt IS NULL " +
                                "AND vt.expiresAt > CURRENT_TIMESTAMP",
                        VerificationToken.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    /**
     * Save a verification token.
     *
     * @param token the verification token to persist
     */
    @Transactional
    public void save(VerificationToken token) {
        entityManager.persist(token);
    }

    /**
     * Update an existing verification token (e.g., mark consumed, set revocation).
     *
     * @param token the verification token to update
     */
    @Transactional
    public void update(VerificationToken token) {
        entityManager.merge(token);
    }

    /**
     * Consume (invalidate) a specific verification token atomically.
     * <p>
     * Marks the token as consumed and links it to its user.
     *
     * @param token the token to consume
     */
    @Transactional
    public void consume(VerificationToken token) {
        token.setConsumedAt(Instant.now());
        update(token);
    }

    /**
     * Revoke a verification token atomically.
     *
     * @param token the token to revoke
     * @param reason the revocation reason
     */
    @Transactional
    public void revoke(VerificationToken token, String reason) {
        token.setRevokedAt(Instant.now());
        token.setRevocationReason(reason);
        update(token);
    }
}