package com.nl2db.auth.dto;

import jakarta.validation.constraints.Future;

import java.time.Instant;

public class ApiKeyRotateRequest {

    @Future(message = "API key expiration must be in the future")
    private Instant expiresAt;

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}