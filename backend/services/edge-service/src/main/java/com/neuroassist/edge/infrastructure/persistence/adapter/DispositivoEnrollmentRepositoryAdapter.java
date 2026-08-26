package com.neuroassist.edge.infrastructure.persistence.adapter;

import com.neuroassist.edge.application.port.out.DispositivoEnrollmentRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoEnrollment;
import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEnrollmentJpaEntity;
import com.neuroassist.edge.infrastructure.persistence.repository.DispositivoEnrollmentJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link DispositivoEnrollmentRepositoryPort} — EDGE-010 (P3). Maps between the
 * pure domain record {@link DispositivoEnrollment} and the JPA entity {@link
 * DispositivoEnrollmentJpaEntity}. No {@code @Transactional} here — transaction boundary belongs to
 * {@code application.service} (RULE-03).
 */
@Component
@RequiredArgsConstructor
public class DispositivoEnrollmentRepositoryAdapter implements DispositivoEnrollmentRepositoryPort {

    private final DispositivoEnrollmentJpaRepository repository;

    @Override
    public DispositivoEnrollment save(DispositivoEnrollment domain) {
        DispositivoEnrollmentJpaEntity entity = toEntity(domain);
        // O mapper sempre constrói uma instância nova; só o repositório sabe se a
        // linha já existe. Sem isto, um update viraria INSERT (C-20).
        if (entity.getId() != null && repository.existsById(entity.getId())) {
            entity.markExisting();
        }
        DispositivoEnrollmentJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<DispositivoEnrollment> findById(UUID id) {
        return repository.findById(id).map(DispositivoEnrollmentRepositoryAdapter::toDomain);
    }

    @Override
    public List<DispositivoEnrollment> findByTenantId(String tenantId) {
        return repository.findByTenantId(tenantId).stream()
                .map(DispositivoEnrollmentRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<DispositivoEnrollment> findByTokenHash(String tokenHash) {
        return repository
                .findByTokenHash(tokenHash)
                .map(DispositivoEnrollmentRepositoryAdapter::toDomain);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private static DispositivoEnrollmentJpaEntity toEntity(DispositivoEnrollment d) {
        return DispositivoEnrollmentJpaEntity.builder()
                .id(d.id())
                .tenantId(d.tenantId())
                .dispositivoEdgeId(d.dispositivoEdgeId())
                .tokenHash(d.tokenHash())
                .expiresAt(d.expiresAt())
                .consumedAt(d.consumedAt())
                .createdAt(d.createdAt())
                .build();
    }

    private static DispositivoEnrollment toDomain(DispositivoEnrollmentJpaEntity e) {
        return new DispositivoEnrollment(
                e.getId(),
                e.getTenantId(),
                e.getDispositivoEdgeId(),
                e.getTokenHash(),
                e.getExpiresAt(),
                e.getConsumedAt(),
                e.getCreatedAt());
    }
}
