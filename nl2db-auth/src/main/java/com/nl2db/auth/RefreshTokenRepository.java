package com.nl2db.auth;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract for RefreshToken persistence.
 * <p>
 * Owned by nl2db-auth. Provides operations for refresh token lifecycle
 * management including lookup by hash, family revocation, and atomic
 * rotation operations.
 * <p>
 * Do not introduce unnecessary generic repository abstractions.
 * This interface stays focused on RefreshToken-specific queries and
 * atomic lifecycle operations.
 */
@Repository
public class RefreshTokenRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Find a refresh token by its stored hash.
     *
     * @param tokenHash the SHA-256 hash of the refresh token
     * @return the refresh token, or null if not found
     */
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return entityManager.createQuery(
                        "SELECT rt FROM RefreshToken rt " +
                                "WHERE rt.tokenHash = :tokenHash " +
                                "AND rt.revokedAt IS NULL " +
                                "AND rt.consumedAt IS NULL",
                        RefreshToken.class)
                .setParameter("tokenHash", tokenHash)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Find a refresh token by hash with pessimistic locking.
     * <p>
     * Uses database-level pessimistic locking to ensure exclusive access
     * for concurrent rotation or revocation operations.
     * Returns null if no token with the given hash exists.
     *
     * @param tokenHash the refresh token hash
     * @return the locked refresh token, or null if not found
     */
    public RefreshToken findByTokenHashLocked(String tokenHash) {
        try {
            return entityManager.createQuery(
                            "SELECT rt FROM RefreshToken rt " +
                                    "WHERE rt.tokenHash = :tokenHash",
                            RefreshToken.class)
                    .setParameter("tokenHash", tokenHash)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            return null;
        }
    }

    /**
     * Lock a refresh token for update with pessimistic writing.
     * <p>
     * Acquires a database-level write lock on the token row, preventing
     * concurrent modifications. This is used during refresh rotation to
     * ensure atomicity and prevent race conditions between concurrent
     * refresh requests using the same token.
     *
     * @param tokenId the refresh token ID to lock
     * @return the locked refresh token
     * @throws IllegalArgumentException if no token with the given ID exists
     */
    public RefreshToken lockTokenForUpdate(UUID tokenId) {
        try {
            return entityManager.createQuery(
                            "SELECT rt FROM RefreshToken rt " +
                                    "WHERE rt.id = :tokenId",
                            RefreshToken.class)
                    .setParameter("tokenId", tokenId)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            throw new IllegalArgumentException("Refresh token not found: " + tokenId);
        }
    }

    /**
     * Find active tokens by user ID.
     *
     * @param userId the user identifier
     * @return list of active (non-consumed, non-revoked) refresh tokens
     */
    public java.util.List<RefreshToken> findByUserId(UUID userId) {
        return entityManager.createQuery(
                        "SELECT rt FROM RefreshToken rt " +
                                "WHERE rt.userId = :userId " +
                                "AND rt.revokedAt IS NULL " +
                                "AND rt.consumedAt IS NULL " +
                                "AND rt.expiresAt > CURRENT_TIMESTAMP",
                        RefreshToken.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    /**
     * Find all tokens in a family, including consumed and revoked ones.
     *
     * @param familyId the token family identifier
     * @return list of all tokens in the family
     */
    public java.util.List<RefreshToken> findByFamilyId(UUID familyId) {
        return entityManager.createQuery(
                        "SELECT rt FROM RefreshToken rt " +
                                "WHERE rt.familyId = :familyId",
                        RefreshToken.class)
                .setParameter("familyId", familyId)
                .getResultList();
    }

    /**
     * Find the most recent (latest issued) active token in a family.
     *
     * @param familyId the token family identifier
     * @return the most recent active token, or null if not found
     */
    public Optional<RefreshToken> findMostRecentActiveByFamilyId(UUID familyId) {
        return entityManager.createQuery(
                        "SELECT rt FROM RefreshToken rt " +
                                "WHERE rt.familyId = :familyId " +
                                "AND rt.revokedAt IS NULL " +
                                "AND rt.consumedAt IS NULL " +
                                "AND rt.expiresAt > CURRENT_TIMESTAMP " +
                                "ORDER BY rt.issuedAt DESC",
                        RefreshToken.class)
                .setParameter("familyId", familyId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Save a refresh token.
     *
     * @param token the refresh token to persist
     */
    @Transactional
    public void save(RefreshToken token) {
        entityManager.persist(token);
    }

    /**
     * Update an existing refresh token (e.g., mark consumed, set revocation).
     *
     * @param token the refresh token to update
     */
    @Transactional
    public void update(RefreshToken token) {
        entityManager.merge(token);
    }

    /**
     * Delete a refresh token.
     *
     * @param token the refresh token to delete
     */
    @Transactional
    public void delete(RefreshToken token) {
        entityManager.remove(entityManager.contains(token) ? token :
                entityManager.merge(token));
    }

    /**
     * Revoke an entire token family atomically.
     * <p>
     * Marks all tokens in the given family as revoked with the specified
     * reason. This is used for logout and reuse detection.
     *
     * @param familyId the token family to revoke
     * @param reason   the revocation reason
     */
    @Transactional
    public void revokeFamily(UUID familyId, String reason) {
        java.util.List<RefreshToken> family = findByFamilyId(familyId);
        for (RefreshToken token : family) {
            token.setRevokedAt(Instant.now());
            token.setRevocationReason(reason);
            update(token);
        }
    }

    /**
     * Consume (invalidate) a specific refresh token atomically.
     * <p>
     * Marks the token as consumed and links it to its replacement.
     * Uses entity manager merge for atomic update.
     *
     * @param token        the token to consume
     * @param replacementId the ID of the replacement token, or null
     */
    @Transactional
    public void consume(RefreshToken token, UUID replacementId) {
        token.setConsumedAt(Instant.now());
        token.setReplacementTokenId(replacementId);
        update(token);
    }
}