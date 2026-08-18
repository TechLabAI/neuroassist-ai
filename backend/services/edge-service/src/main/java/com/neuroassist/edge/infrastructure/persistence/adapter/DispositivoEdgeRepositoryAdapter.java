package com.neuroassist.edge.infrastructure.persistence.adapter;

import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEdgeJpaEntity;
import com.neuroassist.edge.infrastructure.persistence.repository.DispositivoEdgeJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link DispositivoEdgeRepositoryPort} — EDGE-010 (P3). Maps between the pure
 * domain record {@link DispositivoEdge} and the JPA entity {@link DispositivoEdgeJpaEntity}. No
 * {@code @Transactional} here — transaction boundary belongs to {@code application.service}
 * (RULE-03).
 */
@Component
@RequiredArgsConstructor
public class DispositivoEdgeRepositoryAdapter implements DispositivoEdgeRepositoryPort {

    private final DispositivoEdgeJpaRepository repository;

    @Override
    public DispositivoEdge save(DispositivoEdge domain) {
        DispositivoEdgeJpaEntity entity = toEntity(domain);
        DispositivoEdgeJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<DispositivoEdge> findById(UUID id) {
        return repository.findById(id).map(DispositivoEdgeRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<DispositivoEdge> findByIdAndTenantId(UUID id, String tenantId) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .map(DispositivoEdgeRepositoryAdapter::toDomain);
    }

    @Override
    public List<DispositivoEdge> findByTenantId(String tenantId) {
        return repository.findByTenantId(tenantId).stream()
                .map(DispositivoEdgeRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<DispositivoEdge> findByPairingStateAndPacienteIdAndTenantId(
            PairingState state, UUID pacienteId, String tenantId) {
        return repository
                .findByPairingStateAndPacienteIdAndTenantId(state, pacienteId, tenantId)
                .map(DispositivoEdgeRepositoryAdapter::toDomain);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private static DispositivoEdgeJpaEntity toEntity(DispositivoEdge d) {
        return DispositivoEdgeJpaEntity.builder()
                .id(d.id())
                .tenantId(d.tenantId())
                .label(d.label())
                .pacienteId(d.pacienteId())
                .pairingState(d.pairingState())
                .versao(d.versao())
                .ultimaSincronizacao(d.ultimaSincronizacao())
                .runningProfileVersion(d.runningProfileVersion())
                .rowVersion(d.rowVersion())
                .createdAt(d.createdAt())
                .updatedAt(d.updatedAt())
                .createdBy(d.createdBy())
                .updatedBy(d.updatedBy())
                .build();
    }

    private static DispositivoEdge toDomain(DispositivoEdgeJpaEntity e) {
        return new DispositivoEdge(
                e.getId(),
                e.getTenantId(),
                e.getLabel(),
                e.getPacienteId(),
                e.getPairingState(),
                e.getVersao(),
                e.getUltimaSincronizacao(),
                e.getRunningProfileVersion(),
                e.getRowVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getCreatedBy(),
                e.getUpdatedBy());
    }
}
