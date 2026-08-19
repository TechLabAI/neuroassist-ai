package com.neuroassist.edge.infrastructure.persistence.repository;

import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEdgeJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoEdgeJpaRepository
        extends JpaRepository<DispositivoEdgeJpaEntity, UUID> {

    List<DispositivoEdgeJpaEntity> findByTenantId(String tenantId);

    Optional<DispositivoEdgeJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    /** Paged tenant-scoped listing — EDGE P9 endpoint GET /api/v1/edge/devices. */
    Page<DispositivoEdgeJpaEntity> findAllByTenantId(String tenantId, Pageable pageable);

    /**
     * Exclusive-pairing invariant check: at most one {@code paired} device per patient per tenant.
     * Backed by partial unique index {@code ux_dispositivo_edge_active_pairing}.
     */
    Optional<DispositivoEdgeJpaEntity> findByPairingStateAndPacienteIdAndTenantId(
            PairingState pairingState, UUID pacienteId, String tenantId);
}
