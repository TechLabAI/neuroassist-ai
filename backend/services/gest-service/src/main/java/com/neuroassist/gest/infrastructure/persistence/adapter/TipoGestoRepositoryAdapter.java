package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.domain.model.TipoGestoTransition;
import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoJpaEntity;
import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoTransitionJpaEntity;
import com.neuroassist.gest.infrastructure.persistence.repository.TipoGestoJpaRepository;
import com.neuroassist.gest.infrastructure.persistence.repository.TipoGestoTransitionJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TipoGestoRepositoryAdapter implements TipoGestoRepositoryPort {

    private final TipoGestoJpaRepository repository;
    private final TipoGestoTransitionJpaRepository transitionRepository;

    @Override
    public TipoGesto save(TipoGesto entity) {
        TipoGestoJpaEntity jpa = toJpa(entity);
        return toDomain(repository.save(jpa));
    }

    @Override
    public Optional<TipoGesto> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<TipoGesto> findByTenantId(String tenantId) {
        // PoC: single schema — return all records, ignore tenantId
        return repository.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<TipoGesto> findByIdAndState(String id, String state) {
        return repository.findByIdAndDetectabilityState(id, state).map(this::toDomain);
    }

    @Override
    public List<TipoGesto> findByState(String state) {
        return repository.findByDetectabilityState(state)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public TipoGestoTransition saveTransition(TipoGestoTransition transition) {
        TipoGestoTransitionJpaEntity jpa = TipoGestoTransitionJpaEntity.builder()
                .tipoGestoId(transition.tipoGestoId())
                .fromState(transition.fromState())
                .toState(transition.toState())
                .actor(transition.actor())
                .reason(transition.reason())
                .at(transition.at())
                .build();
        TipoGestoTransitionJpaEntity saved = transitionRepository.save(jpa);
        return new TipoGestoTransition(
                saved.getId(),
                saved.getTipoGestoId(),
                saved.getFromState(),
                saved.getToState(),
                saved.getActor(),
                saved.getReason(),
                saved.getAt());
    }

    private TipoGesto toDomain(TipoGestoJpaEntity e) {
        return new TipoGesto(
                e.getId(),
                e.getNome(),
                e.getDetectabilityState(),
                e.getEngineFeatureRef(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private TipoGestoJpaEntity toJpa(TipoGesto d) {
        return TipoGestoJpaEntity.builder()
                .id(d.id())
                .nome(d.nome())
                .detectabilityState(d.detectabilityState())
                .engineFeatureRef(d.engineFeatureRef())
                .createdAt(d.createdAt())
                .updatedAt(d.updatedAt())
                .build();
    }
}
