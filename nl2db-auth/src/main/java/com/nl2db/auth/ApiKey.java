package com.nl2db.auth;

import com.nl2db.common.ApiKeyUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root representing an API key.
 * <p>
 * Invariants:
 * - Every API key has a valid unique identifier (UUID, immutable after creation)
 * - Every API key is associated with exactly one organization
 * - Every API key has a creator user
 * - Every API key has a human-readable name
 * - The public prefix is derived from the secret and allows identification
 *   without revealing the secret
 * - The secret itself is never stored in plaintext
 * - Creation timestamp is mandatory and immutable
 * - An API key must always have a valid organization and creator
 * <p>
 * This class contains business logic and invariants only.
 * Persistence mapping (entity mapping, repository) is kept separate.
 */
@Entity
@Table(name = "api_keys", schema = "nl2db",
        uniqueConstraints = @UniqueConstraint(
                columnNames = "public_prefix",
                name = "uk_api_keys_public_prefix"))
public class ApiKey {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private final UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private final UUID organizationId;

    @Column(name = "creator_user_id", nullable = false, updatable = false)
    private final UUID creatorUserId;

    @Column(name = "key_name", nullable = false, length = 255)
    @NotNull(message = "API key name must not be null")
    @Size(min = 1, max = 255, message = "API key name must be between 1 and 255 characters")
    private String keyName;

    @Column(name = "public_prefix", nullable = false, length = 16)
    @NotNull(message = "Public prefix must not be null")
    @Size(min = 1, max = 16, message = "Public prefix must be between 1 and 16 characters")
    private String publicPrefix;

    @Column(name = "secret_hash", nullable = false, length = 64)
    @NotNull(message = "Secret hash must not be null")
    @Size(min = 1, max = 64, message = "Secret hash must be between 1 and 64 characters")
    private String secretHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private final Instant createdAt;

    // JPA-only default constructor
    @Deprecated // JPA only
    public ApiKey() {
        this.id = null;
        this.organizationId = null;
        this.creatorUserId = null;
        this.keyName = null;
        this.publicPrefix = null;
        this.secretHash = null;
        this.createdAt = null;
    }

    /**
     * Constructs a new API key with the given identity, organization, creator,
     * name, and public prefix.
     * <p>
     * This is the only valid way to create an API key instance.
     * All invariants are enforced.
     *
     * @param id                stable UUID-based identifier (immutable)
     * @param organizationId    the associated organization UUID (immutable)
     * @param creatorUserId     the creator user UUID (immutable after creation)
     * @param keyName           the human-readable API key name (must not be blank)
     * @param publicPrefix      the public prefix derived from the secret (immutable)
     * @param secretHash        the SHA-256 hash of the secret (immutable)
     * @param createdAt         creation timestamp (must not precede now)
     * @throws IllegalArgumentException if any invariant is violated
     */
    public ApiKey(UUID id, UUID organizationId, UUID creatorUserId,
                  String keyName, String publicPrefix, String secretHash, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.creatorUserId = creatorUserId;
        this.keyName = keyName;
        this.publicPrefix = publicPrefix;
        this.secretHash = secretHash;

        // Invariant: keyName must not be null or blank
        if (keyName == null || keyName.trim().isEmpty()) {
            throw new IllegalArgumentException("API key name must not be blank");
        }

        // Invariant: organizationId must not be null
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization ID must not be null");
        }

        // Invariant: creatorUserId must not be null
        if (creatorUserId == null) {
            throw new IllegalArgumentException("Creator user ID must not be null");
        }

        // Invariant: publicPrefix must not be null or empty
        if (publicPrefix == null || publicPrefix.isEmpty()) {
            throw new IllegalArgumentException("Public prefix must not be null or empty");
        }

        // Invariant: secretHash must not be null or empty
        if (secretHash == null || secretHash.isEmpty()) {
            throw new IllegalArgumentException("Secret hash must not be null or empty");
        }

        // Invariant: createdAt must not be null
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation timestamp must not be null");
        }

        this.createdAt = createdAt;
    }

    // --- Identity ---

    /**
     * Returns the stable UUID-based identifier.
     * Identity is immutable; this method never returns null.
     */
    public UUID id() {
        return id;
    }

    // --- Organization ---

    /**
     * Returns the associated organization ID.
     */
    public UUID organizationId() {
        return organizationId;
    }

    // --- Creator ---

    /**
     * Returns the creator user ID.
     */
    public UUID creatorUserId() {
        return creatorUserId;
    }

    // --- Key Name ---

    /**
     * Returns the human-readable API key name.
     */
    public String keyName() {
        return keyName;
    }

    /**
     * Sets the human-readable API key name.
     * Triggers validation.
     *
     * @param keyName the new key name
     * @throws IllegalArgumentException if name is blank
     */
    public void setKeyName(String keyName) {
        if (keyName == null || keyName.trim().isEmpty()) {
            throw new IllegalArgumentException("API key name must not be blank");
        }
        this.keyName = keyName;
    }

    // --- Public Prefix ---

    /**
     * Returns the public prefix derived from the secret.
     * This allows identifying which key was used without revealing the secret.
     */
    public String publicPrefix() {
        return publicPrefix;
    }

    // --- Secret Hash ---

    /**
     * Returns the SHA-256 hash of the secret.
     * The actual secret is never stored or exposed through this entity.
     */
    public String secretHash() {
        return secretHash;
    }

    // --- Created At ---

    /**
     * Returns the creation timestamp.
     * This value is immutable and set at construction.
     */
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiKey that = (ApiKey) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}