package com.neuroassist.edge.infrastructure.persistence.repository;

import com.neuroassist.edge.domain.model.CredentialStatus;
import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoCredentialJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoCredentialJpaRepository
        extends JpaRepository<DispositivoCredentialJpaEntity, UUID> {

    List<DispositivoCredentialJpaEntity> findByTenantId(String tenantId);

    /**
     * Retrieve all credentials for a device (active and revoked) — used during retire / re-pair
     * revocation.
     */
    List<DispositivoCredentialJpaEntity> findByDispositivoEdgeId(UUID dispositivoEdgeId);

    /**
     * Retrieve the single active credential for a device. At most one active credential per device
     * is enforced by partial unique index {@code ux_dispositivo_credential_active}.
     */
    Optional<DispositivoCredentialJpaEntity> findByDispositivoEdgeIdAndStatus(
            UUID dispositivoEdgeId, CredentialStatus status);

    /**
     * Lookup by OAuth2 client_id + status — device credential verification (C-18). {@code clientId}
     * is unique per credential row; combined with {@code active} it yields at most one result.
     */
    Optional<DispositivoCredentialJpaEntity> findByClientIdAndStatus(
            String clientId, CredentialStatus status);
}
