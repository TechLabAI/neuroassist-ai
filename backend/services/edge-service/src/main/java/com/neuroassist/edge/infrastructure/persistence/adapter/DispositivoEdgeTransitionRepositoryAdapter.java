package com.neuroassist.edge.infrastructure.persistence.adapter;

import com.neuroassist.edge.application.port.out.DispositivoEdgeTransitionRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoEdgeTransition;
import com.neuroassist.edge.domain.model.TransitionKind;
import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEdgeTransitionJpaEntity;
import com.neuroassist.edge.infrastructure.persistence.repository.DispositivoEdgeTransitionJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link DispositivoEdgeTransitionRepositoryPort} — EDGE-010 (P3). Append-only — no
 * UPDATE/DELETE operations are ever issued against {@code dispositivo_edge_transition}
 * (data-model.md §2.4, ADR-005 invariant #3). Maps between the pure domain record {@link
 * DispositivoEdgeTransition} and the JPA entity {@link DispositivoEdgeTransitionJpaEntity}. No
 * {@code @Transactional} here — transaction boundary belongs to {@code application.service}
 * (RULE-03).
 */
@Component
@RequiredArgsConstructor
public class DispositivoEdgeTransitionRepositoryAdapter
        implements DispositivoEdgeTransitionRepositoryPort {

    private final DispositivoEdgeTransitionJpaRepository repository;

    @Override
    public DispositivoEdgeTransition save(DispositivoEdgeTransition domain) {
        DispositivoEdgeTransitionJpaEntity entity = toEntity(domain);
        // O mapper sempre constrói uma instância nova; só o repositório sabe se a
        // linha já existe. Sem isto, um update viraria INSERT (C-20).
        if (entity.getId() != null && repository.existsById(entity.getId())) {
            entity.markExisting();
        }
        DispositivoEdgeTransitionJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<DispositivoEdgeTransition> findById(UUID id) {
        return repository.findById(id).map(DispositivoEdgeTransitionRepositoryAdapter::toDomain);
    }

    @Override
    public List<DispositivoEdgeTransition> findByTenantId(String tenantId) {
        return repository.findByTenantId(tenantId).stream()
                .map(DispositivoEdgeTransitionRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<DispositivoEdgeTransition> findByDispositivoEdgeIdOrderByAtDesc(
            UUID deviceId, int limit) {
        return repository
                .findByDispositivoEdgeIdOrderByAtDesc(deviceId, PageRequest.of(0, limit))
                .stream()
                .map(DispositivoEdgeTransitionRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<DispositivoEdgeTransition> findLatestLivenessByDeviceId(
            UUID deviceId, TransitionKind kind) {
        return repository
                .findFirstByDispositivoEdgeIdAndKindOrderByAtDesc(deviceId, kind)
                .map(DispositivoEdgeTransitionRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsLivenessTransitionToOfflineAfter(UUID deviceId, Instant after) {
        return repository.existsByDispositivoEdgeIdAndKindAndToStateAndAtAfter(
                deviceId, TransitionKind.liveness, "offline", after);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private static DispositivoEdgeTransitionJpaEntity toEntity(DispositivoEdgeTransition d) {
        return DispositivoEdgeTransitionJpaEntity.builder()
                .id(d.id())
                .tenantId(d.tenantId())
                .dispositivoEdgeId(d.dispositivoEdgeId())
                .kind(d.kind())
                .fromState(d.fromState())
                .toState(d.toState())
                .actor(d.actor())
                .reason(d.reason())
                .at(d.at())
                .build();
    }

    private static DispositivoEdgeTransition toDomain(DispositivoEdgeTransitionJpaEntity e) {
        return new DispositivoEdgeTransition(
                e.getId(),
                e.getTenantId(),
                e.getDispositivoEdgeId(),
                e.getKind(),
                e.getFromState(),
                e.getToState(),
                e.getActor(),
                e.getReason(),
                e.getAt());
    }
}
