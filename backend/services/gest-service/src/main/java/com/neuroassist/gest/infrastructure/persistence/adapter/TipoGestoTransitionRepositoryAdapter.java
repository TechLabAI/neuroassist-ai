package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.TipoGestoTransitionRepositoryPort;
import com.neuroassist.gest.domain.model.TipoGestoTransition;
import com.neuroassist.gest.infrastructure.persistence.repository.TipoGestoTransitionJpaRepository;
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
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<TipoGestoTransition> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<TipoGestoTransition> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
