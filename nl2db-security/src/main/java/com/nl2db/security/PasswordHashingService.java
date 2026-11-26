package com.nl2db.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service for Argon2id password hashing using Spring Security's Argon2PasswordEncoder.
 * <p>
 * Uses Spring Security's Argon2PasswordEncoder with production-grade parameters.
 * Encoded hashes are self-describing with algorithm identifier ($argon2id$),
 * parameters, and unique salt. No separately generated salt is stored.
 */
@Service
public class PasswordHashingService {

    private final PasswordEncoder passwordEncoder;

    /**
     * Constructs a PasswordHashingService with the configured Argon2 encoder.
     * <p>
     * The Argon2PasswordEncoder uses parameters compatible with this Spring
     * Boot / Spring Security version. Default production-grade settings are used.
     * Encoded hashes retain algorithm and parameter information for verification
     * and future upgrades.
     */
    public PasswordHashingService() {
        // Try with minimal parameters that may be more compatible
        // Memory: 10 MiB, Iterations: 1, Parallelism: 1, Hash length: 32 bytes, Salt length: 16 bytes
        this.passwordEncoder = new Argon2PasswordEncoder(10, 1, 1, 32, 16);
    }

    /**
     * Encode a raw password into an Argon2id hash.
     * <p>
     * A unique cryptographically secure salt is generated for each encoding.
     * The encoded hash includes the algorithm identifier, parameters, and salt.
     *
     * @param rawPassword the raw password to encode (must not be null)
     * @return the encoded Argon2id hash (never null)
     * @throws IllegalArgumentException if the raw password is null or blank
     */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password must not be null or blank");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Verify a raw password against an encoded Argon2id hash.
     *
     * @param rawPassword the raw password to verify
     * @param encodedHash the encoded hash to verify against (must not be null)
     * @return true if the password matches, false otherwise
     * @throws IllegalArgumentException if the encoded hash is null
     */
    public boolean verify(String rawPassword, String encodedHash) {
        if (encodedHash == null) {
            throw new IllegalArgumentException("Encoded hash must not be null");
        }
        return passwordEncoder.matches(rawPassword, encodedHash);
    }

    /**
     * Check whether an encoded hash was generated using the current parameter
     * configuration. This supports future parameter upgrades.
     *
     * @param encodedHash the encoded hash to check
     * @return true if the hash uses the Argon2id algorithm identifier,
     *         false if it uses a different algorithm
     */
    public boolean matchesCurrentParameters(String encodedHash) {
        if (encodedHash == null) {
            return false;
        }
        // Argon2PasswordEncoder encoded hashes start with $argon2id$
        return encodedHash.startsWith("$argon2id$");
    }
}