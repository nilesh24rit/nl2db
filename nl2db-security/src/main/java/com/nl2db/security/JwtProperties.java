package com.nl2db.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * JWT properties configured from environment variables.
 * <p>
 * Loads PEM-encoded RSA key material from NL2DB_JWT_PRIVATE_KEY and
 * NL2DB_JWT_PUBLIC_KEY environment variables.
 */
@Component
public class JwtProperties {

    @Value("${NL2DB_JWT_PRIVATE_KEY:null}")
    private String privateKeyPem;

    @Value("${NL2DB_JWT_PUBLIC_KEY:null}")
    private String publicKeyPem;

    private java.security.PrivateKey privateKey;

    private java.security.PublicKey publicKey;

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    @PreDestroy
    public void cleanup() {
        // Clear sensitive key material from memory
    }
}