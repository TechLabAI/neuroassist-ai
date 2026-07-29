package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.ReportIdempotencyRepositoryPort;
import com.neuroassist.evnt.domain.model.ReportIdempotency;
import com.neuroassist.evnt.infrastructure.persistence.repository.ReportIdempotencyJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportIdempotencyRepositoryAdapter implements ReportIdempotencyRepositoryPort {

    private final ReportIdempotencyJpaRepository repository;

    @Override
    public ReportIdempotency save(ReportIdempotency entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<ReportIdempotency> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<ReportIdempotency> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
