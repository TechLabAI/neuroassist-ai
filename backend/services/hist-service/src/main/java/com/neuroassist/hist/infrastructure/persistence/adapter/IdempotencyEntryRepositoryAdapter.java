package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.IdempotencyEntryRepositoryPort;
import com.neuroassist.hist.domain.model.IdempotencyEntry;
import com.neuroassist.hist.infrastructure.persistence.repository.IdempotencyEntryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdempotencyEntryRepositoryAdapter implements IdempotencyEntryRepositoryPort {

    private final IdempotencyEntryJpaRepository repository;

    @Override
    public IdempotencyEntry save(IdempotencyEntry entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<IdempotencyEntry> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<IdempotencyEntry> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
