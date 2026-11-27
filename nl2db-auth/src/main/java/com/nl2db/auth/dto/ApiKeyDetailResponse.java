package com.nl2db.auth.dto;

import com.nl2db.common.ApiKeyStatus;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyDetailResponse(
        UUID id,
        UUID creatorUserId,
        String name,
        String publicPrefix,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant rotatedAt,
        Instant lastUsedAt,
        UUID replacedByKeyId,
        ApiKeyStatus status
) {
}