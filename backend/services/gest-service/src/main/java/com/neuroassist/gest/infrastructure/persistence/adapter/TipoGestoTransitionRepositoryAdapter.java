package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.TipoGestoTransitionRepositoryPort;
import com.neuroassist.gest.domain.model.TipoGestoTransition;
import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoTransitionJpaEntity;
import com.neuroassist.gest.infrastructure.persistence.repository.TipoGestoTransitionJpaRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TipoGestoTransitionRepositoryAdapter implements TipoGestoTransitionRepositoryPort {

    private final TipoGestoTransitionJpaRepository repository;

    @Override
    public TipoGestoTransition save(TipoGestoTransition entity) {
        TipoGestoTransitionJpaEntity jpa = TipoGestoTransitionJpaEntity.builder()
                .tipoGestoId(entity.tipoGestoId())
                .fromState(entity.fromState())
                .toState(entity.toState())
                .actor(entity.actor())
                .reason(entity.reason())
                .at(entity.at())
                .build();
        TipoGestoTransitionJpaEntity saved = repository.save(jpa);
        return toDomain(saved);
    }

    @Override
    public Optional<TipoGestoTransition> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<TipoGestoTransition> findByTenantId(String tenantId) {
        return Collections.emptyList();
    }

    private TipoGestoTransition toDomain(TipoGestoTransitionJpaEntity e) {
        return new TipoGestoTransition(
                e.getId(),
                e.getTipoGestoId(),
                e.getFromState(),
                e.getToState(),
                e.getActor(),
                e.getReason(),
                e.getAt());
    }
}
