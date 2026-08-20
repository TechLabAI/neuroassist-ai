package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.ActorRefRepositoryPort;
import com.neuroassist.hist.domain.model.ActorRef;
import com.neuroassist.hist.infrastructure.persistence.repository.ActorRefJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActorRefRepositoryAdapter implements ActorRefRepositoryPort {

    private final ActorRefJpaRepository repository;

    @Override
    public ActorRef save(ActorRef entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<ActorRef> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<ActorRef> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
