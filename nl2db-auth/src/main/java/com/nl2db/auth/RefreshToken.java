package com.nl2db.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token persistence model.
 * <p>
 * Responsibilities:
 * - Stores only the SHA-256 hash of the refresh token (never raw token)
 * - Links tokens to users via foreign key
 * - Supports token families for rotation and revocation
 * - Tracks consumption, revocation, and expiration
 * - Enables reuse detection via family-wide revocation
 * <p>
 * Invariants:
 * - Token hash is unique across the table (enforced by constraint)
 * - User relationship is enforced with foreign key
 * - A token can be consumed only once
 * - Every family has a definitive absolute expiration
 * - Raw token value is never persisted or exposed
 */
@Entity
@Table(name = "refresh_tokens", schema = "nl2db",
        uniqueConstraints = @UniqueConstraint(
                columnNames = "token_hash",
                name = "uk_refresh_tokens_token_hash"))
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private final UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private final UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private final String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private final UUID familyId;

    @Column(name = "issued_at", nullable = false, columnDefinition = "timestamp with time zone")
    private final Instant issuedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replacement_token_id")
    private UUID replacementTokenId;

    @Column(name = "revocation_reason")
    private String revocationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    // Default constructor for JPA/Hibernate
    @Deprecated // JPA only
    public RefreshToken() {
        this.id = null;
        this.userId = null;
        this.tokenHash = null;
        this.familyId = null;
        this.issuedAt = null;
        this.expiresAt = null;
        this.consumedAt = null;
        this.revokedAt = null;
        this.replacementTokenId = null;
    }

    /**
     * Constructs a new RefreshToken.
     * <p>
     * This is the only valid way to create a RefreshToken instance.
     * All invariants are enforced.
     *
     * @param id          stable UUID-based identifier
     * @param userId      the associated user identifier
     * @param tokenHash   SHA-256 hash of the raw refresh token
     * @param familyId    token family identifier
     * @param issuedAt    issuance timestamp
     * @param expiresAt   expiration timestamp
     */
    public RefreshToken(UUID id, UUID userId, String tokenHash, UUID familyId,
                        Instant issuedAt, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.consumedAt = null;
        this.revokedAt = null;
        this.replacementTokenId = null;
        this.revocationReason = null;
    }

    // --- Identity ---

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public UUID familyId() {
        return familyId;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActive() {
        return !isConsumed() && !isRevoked() && !isExpired();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UUID replacementTokenId() {
        return replacementTokenId;
    }

    public void setReplacementTokenId(UUID replacementTokenId) {
        this.replacementTokenId = replacementTokenId;
    }

    public String revocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public User user() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}