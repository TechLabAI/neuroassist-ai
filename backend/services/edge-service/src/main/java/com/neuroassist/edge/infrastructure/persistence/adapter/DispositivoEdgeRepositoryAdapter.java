package com.neuroassist.edge.infrastructure.persistence.adapter;

import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEdgeJpaEntity;
import com.neuroassist.edge.infrastructure.persistence.repository.DispositivoEdgeJpaRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link DispositivoEdgeRepositoryPort} — EDGE-010 (P3). Maps between the pure
 * domain record {@link DispositivoEdge} and the JPA entity {@link DispositivoEdgeJpaEntity}. No
 * {@code @Transactional} here — transaction boundary belongs to {@code application.service}
 * (RULE-03).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispositivoEdgeRepositoryAdapter implements DispositivoEdgeRepositoryPort {

    private final DispositivoEdgeJpaRepository repository;

    @Override
    public DispositivoEdge save(DispositivoEdge domain) {
        DispositivoEdgeJpaEntity entity = toEntity(domain);
        // O mapper sempre constrói uma instância nova; só o repositório sabe se a
        // linha já existe. Sem isto, um update viraria INSERT (C-20).
        if (entity.getId() != null && repository.existsById(entity.getId())) {
            entity.markExisting();
        }
        DispositivoEdgeJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<DispositivoEdge> findById(UUID id) {
        return repository.findById(id).map(DispositivoEdgeRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<DispositivoEdge> findByIdAndTenantId(UUID id, String tenantId) {
        try {
            return repository
                    .findByIdAndTenantId(id, tenantId)
                    .map(DispositivoEdgeRepositoryAdapter::toDomain);
        } catch (DataAccessException e) {
            // Only swallow "undefined table" (42P01) or "invalid schema name" (3F000) —
            // both signal that the tenant schema does not exist (unknown tenant).
            // Any other DataAccessException (connection failure, timeout, deadlock,
            // constraint violation…) must propagate so infrastructure issues are not
            // silently masked as 404. We inspect the SQLState instead of matching by
            // message to avoid fragility across Postgres versions or locales.
            if (!isUnknownTenantSchema(e)) {
                throw e;
            }
            log.warn(
                    "Unknown tenant schema for device={} tenant={} (SQLState={}): {}",
                    id,
                    tenantId,
                    sqlStateOf(e),
                    e.getMostSpecificCause().getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<DispositivoEdge> findByTenantId(String tenantId) {
        return repository.findByTenantId(tenantId).stream()
                .map(DispositivoEdgeRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Page<DispositivoEdge> findAllByTenant(String tenantId, Pageable pageable) {
        return repository
                .findAllByTenantId(tenantId, pageable)
                .map(DispositivoEdgeRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<DispositivoEdge> findByPairingStateAndPacienteIdAndTenantId(
            PairingState state, UUID pacienteId, String tenantId) {
        try {
            return repository
                    .findByPairingStateAndPacienteIdAndTenantId(state, pacienteId, tenantId)
                    .map(DispositivoEdgeRepositoryAdapter::toDomain);
        } catch (DataAccessException e) {
            if (!isUnknownTenantSchema(e)) {
                throw e;
            }
            log.warn(
                    "Unknown tenant schema for patient={} tenant={} (SQLState={}): {}",
                    pacienteId,
                    tenantId,
                    sqlStateOf(e),
                    e.getMostSpecificCause().getMessage());
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Tenant-schema error detection
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} only when {@code e} represents an "undefined table" ({@code 42P01}) or
     * "invalid schema name" ({@code 3F000}) error — both indicate that the tenant schema does not
     * exist (unknown/unprovisioned tenant). Every other {@link DataAccessException} — connection
     * failure, timeout, deadlock, constraint violation — must propagate.
     */
    private static boolean isUnknownTenantSchema(DataAccessException e) {
        String state = sqlStateOf(e);
        return "42P01".equals(state) || "3F000".equals(state);
    }

    /** Walks the cause chain looking for a {@link SQLException} and returns its SQL state. */
    private static String sqlStateOf(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof SQLException sqle) {
            return sqle.getSQLState();
        }
        // getMostSpecificCause already drills to the bottom; still walk manually as fallback.
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException sqle) {
                return sqle.getSQLState();
            }
            t = t.getCause();
        }
        return null;
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
