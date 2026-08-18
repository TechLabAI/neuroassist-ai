package com.neuroassist.edge.infrastructure.persistence.repository;

import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEdgeJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoEdgeJpaRepository
        extends JpaRepository<DispositivoEdgeJpaEntity, UUID> {

    List<DispositivoEdgeJpaEntity> findByTenantId(String tenantId);

    Optional<DispositivoEdgeJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    /**
     * Exclusive-pairing invariant check: at most one {@code paired} device per patient per tenant.
     * Backed by partial unique index {@code ux_dispositivo_edge_active_pairing}.
     */
    Optional<DispositivoEdgeJpaEntity> findByPairingStateAndPacienteIdAndTenantId(
            PairingState pairingState, UUID pacienteId, String tenantId);
}
