package com.neuroassist.edge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-device OAuth2 client_credentials material. Only the SHA-256 hash of the secret is persisted —
 * plaintext returned once at enrollment. At most one {@code active} credential per device (unique
 * partial index in DB). Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public record DispositivoCredential(
        UUID id,
        String tenantId,
        UUID dispositivoEdgeId,
        String clientId,
        String secretHash,
        CredentialStatus status,
        Instant issuedAt,
        Instant revokedAt) {

    public static DispositivoCredential issue(
            UUID id, String tenantId, UUID deviceId, String clientId, String secretHash) {
        return new DispositivoCredential(
                id,
                tenantId,
                deviceId,
                clientId,
                secretHash,
                CredentialStatus.active,
                Instant.now(),
                null);
    }

    public DispositivoCredential withRevoked() {
        return new DispositivoCredential(
                id,
                tenantId,
                dispositivoEdgeId,
                clientId,
                secretHash,
                CredentialStatus.revoked,
                issuedAt,
                Instant.now());
    }

    public boolean isActive() {
        return status == CredentialStatus.active;
    }
}
