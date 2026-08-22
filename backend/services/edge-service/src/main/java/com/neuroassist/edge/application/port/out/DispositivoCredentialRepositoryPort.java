package com.neuroassist.edge.application.port.out;

import com.neuroassist.edge.domain.model.DispositivoCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispositivoCredentialRepositoryPort {

    DispositivoCredential save(DispositivoCredential entity);

    Optional<DispositivoCredential> findById(UUID id);

    List<DispositivoCredential> findByTenantId(String tenantId);

    /**
     * Retrieve the single active credential for a device. At most one active credential per device
     * enforced by partial unique index.
     */
    Optional<DispositivoCredential> findActiveByDispositivoEdgeId(UUID deviceId);

    /** All credentials for a device (including revoked) — used during retire/re-pair revocation. */
    List<DispositivoCredential> findByDispositivoEdgeId(UUID deviceId);

    /**
     * Active credential by {@code clientId} — used by {@code POST
     * /internal/devices/verify-credential} (C-18). Requires the tenant already bound, since {@code
     * dispositivo_credential} lives in the tenant schema.
     */
    Optional<DispositivoCredential> findActiveByClientId(String clientId);
}
