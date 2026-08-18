package com.neuroassist.edge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One-time, TTL-bounded bootstrap-trust token (doc 06 G4). Only the SHA-256 hash is stored —
 * plaintext token is returned to caller exactly once. Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public record DispositivoEnrollment(
        UUID id,
        String tenantId,
        UUID dispositivoEdgeId,
        String tokenHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt) {

    public static DispositivoEnrollment mint(
            UUID id, String tenantId, UUID deviceId, String tokenHash, Instant expiresAt) {
        return new DispositivoEnrollment(
                id, tenantId, deviceId, tokenHash, expiresAt, null, Instant.now());
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isValid() {
        return !isExpired() && !isConsumed();
    }

    public DispositivoEnrollment withConsumed() {
        return new DispositivoEnrollment(
                id, tenantId, dispositivoEdgeId, tokenHash, expiresAt, Instant.now(), createdAt);
    }
}
