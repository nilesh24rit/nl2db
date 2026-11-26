package com.nl2db.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT configuration beans for RSA key material.
 * <p>
 * Provides beans for the RSA keys used by JwtTokenProvider. Key material
 * is validated at bean initialization and fails fast if required production
 * configuration is absent.
 */
@Configuration
public class JwtConfiguration {

    @Bean
    private Key privateKey(JwtProperties jwtProperties) {
        String pem = jwtProperties.getPrivateKeyPem();
        if (pem == null) {
            throw new IllegalArgumentException(
                    "JWT private key material must be configured via NL2DB_JWT_PRIVATE_KEY environment variable");
        }
        String base64 = stripPemHeaders(pem);
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT private key format: " + e.getMessage(), e);
        }
    }

    @Bean
    private Key publicKey(JwtProperties jwtProperties) {
        String pem = jwtProperties.getPublicKeyPem();
        if (pem == null) {
            throw new IllegalArgumentException(
                    "JWT public key material must be configured via NL2DB_JWT_PUBLIC_KEY environment variable");
        }
        String base64 = stripPemHeaders(pem);
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT public key format: " + e.getMessage(), e);
        }
    }

    private String stripPemHeaders(String pem) {
        String lines[] = pem.split("\n");
        StringBuilder content = new StringBuilder();
        boolean inHeader = false;
        for (String line : lines) {
            if (line.startsWith("-----BEGIN")) {
                inHeader = true;
                continue;
            } else if (line.startsWith("-----END")) {
                inHeader = false;
                continue;
            }
            if (inHeader) {
                content.append(line);
            }
        }
        return content.toString().trim();
    }
}