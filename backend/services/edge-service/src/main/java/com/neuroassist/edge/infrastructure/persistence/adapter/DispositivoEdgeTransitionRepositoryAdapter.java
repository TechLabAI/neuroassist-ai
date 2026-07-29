package com.neuroassist.edge.infrastructure.persistence.adapter;

import com.neuroassist.edge.application.port.out.DispositivoEdgeTransitionRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoEdgeTransition;
import com.neuroassist.edge.infrastructure.persistence.repository.DispositivoEdgeTransitionJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DispositivoEdgeTransitionRepositoryAdapter
        implements DispositivoEdgeTransitionRepositoryPort {

    private final DispositivoEdgeTransitionJpaRepository repository;

    @Override
    public DispositivoEdgeTransition save(DispositivoEdgeTransition entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<DispositivoEdgeTransition> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<DispositivoEdgeTransition> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
