package com.neuroassist.edge.application.port.out;

import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.PairingState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DispositivoEdgeRepositoryPort {

    DispositivoEdge save(DispositivoEdge entity);

    Optional<DispositivoEdge> findById(UUID id);

    /** Tenant-safe lookup — returns empty if the device exists but belongs to another tenant. */
    Optional<DispositivoEdge> findByIdAndTenantId(UUID id, String tenantId);

    List<DispositivoEdge> findByTenantId(String tenantId);

    /** Paged tenant-scoped listing for GET /api/v1/edge/devices (EDGE P9). */
    Page<DispositivoEdge> findAllByTenant(String tenantId, Pageable pageable);

    /**
     * Exclusive-pairing invariant check (§7.1 / api-contract). Finds the unique paired device for a
     * patient within a tenant.
     */
    Optional<DispositivoEdge> findByPairingStateAndPacienteIdAndTenantId(
            PairingState state, UUID pacienteId, String tenantId);
}
