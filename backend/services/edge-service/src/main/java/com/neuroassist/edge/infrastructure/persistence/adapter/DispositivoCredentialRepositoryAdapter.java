package com.neuroassist.edge.infrastructure.persistence.adapter;

import com.neuroassist.edge.application.port.out.DispositivoCredentialRepositoryPort;
import com.neuroassist.edge.domain.model.CredentialStatus;
import com.neuroassist.edge.domain.model.DispositivoCredential;
import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoCredentialJpaEntity;
import com.neuroassist.edge.infrastructure.persistence.repository.DispositivoCredentialJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link DispositivoCredentialRepositoryPort} — EDGE-010 (P3). Maps between the
 * pure domain record {@link DispositivoCredential} and the JPA entity {@link
 * DispositivoCredentialJpaEntity}. No {@code @Transactional} here — transaction boundary belongs to
 * {@code application.service} (RULE-03).
 */
@Component
@RequiredArgsConstructor
public class DispositivoCredentialRepositoryAdapter implements DispositivoCredentialRepositoryPort {

    private final DispositivoCredentialJpaRepository repository;

    @Override
    public DispositivoCredential save(DispositivoCredential domain) {
        DispositivoCredentialJpaEntity entity = toEntity(domain);
        DispositivoCredentialJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<DispositivoCredential> findById(UUID id) {
        return repository.findById(id).map(DispositivoCredentialRepositoryAdapter::toDomain);
    }

    @Override
    public List<DispositivoCredential> findByTenantId(String tenantId) {
        return repository.findByTenantId(tenantId).stream()
                .map(DispositivoCredentialRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<DispositivoCredential> findActiveByDispositivoEdgeId(UUID deviceId) {
        return repository
                .findByDispositivoEdgeIdAndStatus(deviceId, CredentialStatus.active)
                .map(DispositivoCredentialRepositoryAdapter::toDomain);
    }

    @Override
    public List<DispositivoCredential> findByDispositivoEdgeId(UUID deviceId) {
        return repository.findByDispositivoEdgeId(deviceId).stream()
                .map(DispositivoCredentialRepositoryAdapter::toDomain)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private static DispositivoCredentialJpaEntity toEntity(DispositivoCredential d) {
        return DispositivoCredentialJpaEntity.builder()
                .id(d.id())
                .tenantId(d.tenantId())
                .dispositivoEdgeId(d.dispositivoEdgeId())
                .clientId(d.clientId())
                .secretHash(d.secretHash())
                .status(d.status())
                .issuedAt(d.issuedAt())
                .revokedAt(d.revokedAt())
                .build();
    }

    private static DispositivoCredential toDomain(DispositivoCredentialJpaEntity e) {
        return new DispositivoCredential(
                e.getId(),
                e.getTenantId(),
                e.getDispositivoEdgeId(),
                e.getClientId(),
                e.getSecretHash(),
                e.getStatus(),
                e.getIssuedAt(),
                e.getRevokedAt());
    }
}
