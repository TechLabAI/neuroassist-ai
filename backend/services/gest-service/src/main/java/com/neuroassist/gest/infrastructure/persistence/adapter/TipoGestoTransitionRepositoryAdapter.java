package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.TipoGestoTransitionRepositoryPort;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.TipoGestoTransition;
import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoTransitionJpaEntity;
import com.neuroassist.gest.infrastructure.persistence.repository.TipoGestoTransitionJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link TipoGestoTransitionRepositoryPort}. Append-only — records are never
 * updated or deleted by the application after creation (invariant #4). Implemented in P3
 * (GEST-011).
 */
@Component
@RequiredArgsConstructor
public class TipoGestoTransitionRepositoryAdapter implements TipoGestoTransitionRepositoryPort {

    private final TipoGestoTransitionJpaRepository repository;

    @Override
    public TipoGestoTransition save(TipoGestoTransition transition) {
        TipoGestoTransitionJpaEntity entity = toEntity(transition);
        TipoGestoTransitionJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<TipoGestoTransition> findByTipoGestoId(UUID tipoGestoId) {
        return repository.findByTipoGestoId(tipoGestoId).stream().map(this::toDomain).toList();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Mapping helpers — domain ↔ JPA entity
    // ────────────────────────────────────────────────────────────────────────

    private TipoGestoTransitionJpaEntity toEntity(TipoGestoTransition t) {
        return TipoGestoTransitionJpaEntity.builder()
                .id(t.id())
                .tipoGestoId(t.tipoGestoId())
                // fromState is nullable for the initial REGISTERED transition (no prior state).
                .fromState(t.fromState() != null ? t.fromState().name().toLowerCase() : null)
                .toState(t.toState().name().toLowerCase())
                .actor(t.actor())
                .reason(t.reason())
                .at(t.at())
                .build();
    }

    private TipoGestoTransition toDomain(TipoGestoTransitionJpaEntity entity) {
        DetectabilityState fromState =
                entity.getFromState() != null
                        ? DetectabilityState.valueOf(entity.getFromState().toUpperCase())
                        : null;
        DetectabilityState toState = DetectabilityState.valueOf(entity.getToState().toUpperCase());
        return new TipoGestoTransition(
                entity.getId(),
                entity.getTipoGestoId(),
                fromState,
                toState,
                entity.getActor(),
                entity.getReason(),
                entity.getAt());
    }
}
