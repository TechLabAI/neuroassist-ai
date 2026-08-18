package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.StreamHeadRepositoryPort;
import com.neuroassist.hist.domain.model.StreamHead;
import com.neuroassist.hist.infrastructure.persistence.repository.StreamHeadJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StreamHeadRepositoryAdapter implements StreamHeadRepositoryPort {

    private final StreamHeadJpaRepository repository;

    @Override
    public StreamHead save(StreamHead entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<StreamHead> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<StreamHead> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
