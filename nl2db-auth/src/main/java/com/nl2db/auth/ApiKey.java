package com.nl2db.auth;

import com.nl2db.common.ApiKeyStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys", schema = "nl2db",
        uniqueConstraints = @UniqueConstraint(
                columnNames = "public_prefix",
                name = "uk_api_keys_public_prefix"))
public class ApiKey {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "creator_user_id", nullable = false, updatable = false)
    private UUID creatorUserId;

    @Column(name = "key_name", nullable = false, length = 255)
    private String keyName;

    @Column(name = "public_prefix", nullable = false, length = 16)
    private String publicPrefix;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "replaced_by_key_id")
    private UUID replacedByKeyId;

    @Deprecated
    public ApiKey() {
    }

    public ApiKey(UUID id, UUID organizationId, UUID creatorUserId, String keyName,
                  String publicPrefix, String secretHash, Instant createdAt, Instant expiresAt) {
        if (id == null) {
            throw new IllegalArgumentException("API key ID must not be null");
        }
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization ID must not be null");
        }
        if (creatorUserId == null) {
            throw new IllegalArgumentException("Creator user ID must not be null");
        }
        if (keyName == null || keyName.isBlank()) {
            throw new IllegalArgumentException("API key name must not be blank");
        }
        if (publicPrefix == null || publicPrefix.isBlank()) {
            throw new IllegalArgumentException("Public prefix must not be blank");
        }
        if (secretHash == null || secretHash.isBlank()) {
            throw new IllegalArgumentException("Secret hash must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation timestamp must not be null");
        }
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Expiration must be after creation");
        }

        this.id = id;
        this.organizationId = organizationId;
        this.creatorUserId = creatorUserId;
        this.keyName = keyName;
        this.publicPrefix = publicPrefix;
        this.secretHash = secretHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public UUID creatorUserId() {
        return creatorUserId;
    }

    public String keyName() {
        return keyName;
    }

    public String publicPrefix() {
        return publicPrefix;
    }

    public String secretHash() {
        return secretHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public Instant rotatedAt() {
        return rotatedAt;
    }

    public UUID replacedByKeyId() {
        return replacedByKeyId;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && rotatedAt == null && !isExpired(now);
    }

    public ApiKeyStatus status(Instant now) {
        if (rotatedAt != null) {
            return ApiKeyStatus.ROTATED;
        }
        if (revokedAt != null) {
            return ApiKeyStatus.REVOKED;
        }
        if (isExpired(now)) {
            return ApiKeyStatus.EXPIRED;
        }
        return ApiKeyStatus.ACTIVE;
    }

    public void revoke(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Revocation timestamp must not be null");
        }
        if (rotatedAt != null) {
            throw new IllegalStateException("A rotated API key cannot be revoked again");
        }
        if (revokedAt != null) {
            return;
        }
        this.revokedAt = now;
    }

    public void markRotated(Instant now, UUID replacementId) {
        if (now == null || replacementId == null) {
            throw new IllegalArgumentException("Rotation timestamp and replacement are required");
        }
        if (rotatedAt != null) {
            throw new IllegalStateException("API key has already been rotated");
        }
        if (revokedAt != null) {
            throw new IllegalStateException("A revoked API key cannot be rotated");
        }
        this.rotatedAt = now;
        this.revokedAt = now;
        this.replacedByKeyId = replacementId;
    }

    public void recordLastUsed(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Last-used timestamp must not be null");
        }
        this.lastUsedAt = now;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiKey apiKey)) return false;
        return id.equals(apiKey.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}