package com.nl2db.auth;

import jakarta.persistence.*;

/**
 * Credential entity representing a user's password hash.
 * <p>
 * Invariants:
 * - A credential belongs to exactly one user (owned relationship)
 * - The user-credential relationship is unique (one credential per user)
 * - Only the encoded Argon2id hash is stored; raw passwords are never persisted
 * - The encoded hash retains algorithm and parameter information for verification
 * - The credential cannot be retrieved through public user lookup methods
 * - Credential and User persistence are transactionally consistent
 * <p>
 * This entity is intentionally kept separate from the User aggregate to
 * enforce the ownership boundary and enable independent validation of the
 * credential lifecycle. Access to this entity is restricted to internal
 * repository methods; it is not exposed through public user serialization.
 * <p>
 * The hash field stores the self-describing Argon2id encoded hash (e.g.
 * "$argon2id$m=19456,t=2,p=1$...") which includes the algorithm identifier,
 * parameters, and a unique cryptographically secure salt. No separately generated
 * salt is stored alongside the hash.
 */
@Entity
@Table(name = "credentials", schema = "nl2db",
        uniqueConstraints = @UniqueConstraint(
                columnNames = "user_id",
                name = "uk_credentials_user_id"))
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "hash", nullable = false, length = 255)
    private String hash;

    protected Credential() {
        // Default constructor for JPA/Hibernate
    }

    /**
     * Constructs a new Credential for the given user with the encoded password hash.
     *
     * @param user       the user this credential belongs to
     * @param hashArgon2id the encoded Argon2id hash (must not be null)
     */
    public Credential(User user, String hashArgon2id) {
        this.user = user;
        this.hash = hashArgon2id;
    }

    /**
     * Returns the unique identifier for this credential.
     */
    public Long id() {
        return id;
    }

    /**
     * Returns the user this credential belongs to.
     */
    public User user() {
        return user;
    }

    /**
     * Returns the encoded Argon2id password hash.
     * <p>
     * This hash includes the algorithm identifier, parameters, and unique salt.
     * Never stores raw passwords or separately generated salts.
     */
    public String hash() {
        return hash;
    }
}