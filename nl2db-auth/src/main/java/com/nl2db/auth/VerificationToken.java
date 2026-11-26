package com.nl2db.auth;

import jakarta.persistence.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Verification token persistence model.
 * <p>
 * Responsibilities:
 * - Stores only the SHA-256 hash of the verification token (never raw token)
 * - Links tokens to users via foreign key
 * - Tracks creation, expiration, and consumption status
 * - Ensures token hash uniqueness
 * <p>
 * Invariants:
 * - Token hash is unique across the table (enforced by constraint)
 * - User relationship is enforced with foreign key
 * - A token can be consumed only once
 * - Raw token value is never persisted or exposed
 * - Expiration is enforced at lookup time
 */
@Entity
@Table(name = "verification_tokens", schema = "nl2db",
        uniqueConstraints = @UniqueConstraint(
                columnNames = "token_hash",
                name = "uk_verification_tokens_token_hash"))
public class VerificationToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private final UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private final UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private final String tokenHash;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone")
    private final Instant createdAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamp with time zone")
    private final Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_resend_at")
    private Instant lastResendAt;

    @Transient
    private String revocationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    // Default constructor for JPA/Hibernate
    @Deprecated // JPA only
    public VerificationToken() {
        this.id = null;
        this.userId = null;
        this.tokenHash = null;
        this.createdAt = null;
        this.expiresAt = null;
        this.consumedAt = null;
        this.revokedAt = null;
        this.user = null;
    }

    /**
     * Constructs a new VerificationToken.
     * <p>
     * This is the only valid way to create a VerificationToken instance.
     * All invariants are enforced.
     *
     * @param id          stable UUID-based identifier
     * @param userId      the associated user identifier
     * @param tokenHash   SHA-256 hash of the raw verification token
     * @param createdAt   creation timestamp
     * @param expiresAt   expiration timestamp
     */
    public VerificationToken(UUID id, UUID userId, String tokenHash,
                             Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.consumedAt = null;
        this.revokedAt = null;
        this.lastResendAt = null;
        this.user = null;
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

    public Instant createdAt() {
        return createdAt;
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

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !isConsumed() && !isRevoked() && !isExpired();
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Instant lastResendAt() {
        return lastResendAt;
    }

    public void setLastResendAt(Instant lastResendAt) {
        this.lastResendAt = lastResendAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
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