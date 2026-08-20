package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.IdempotencyEntryRepositoryPort;
import com.neuroassist.hist.domain.model.IdempotencyEntry;
import com.neuroassist.hist.infrastructure.persistence.repository.IdempotencyEntryJpaRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** P3 stub — idempotency entry persistence adapter. Implemented in HIST-010. */
@Component
@RequiredArgsConstructor
public class IdempotencyEntryRepositoryAdapter implements IdempotencyEntryRepositoryPort {

    private final IdempotencyEntryJpaRepository repository;

    @Override
    public IdempotencyEntry save(IdempotencyEntry entry, String tenantId) {
        // TODO: P3 — map domain model → JPA entity → persist → map back
        throw new UnsupportedOperationException("not implemented — deferred to P3");
    }

    @Override
    public Optional<IdempotencyEntry> findByFactIdAndTenantId(UUID factId, String tenantId) {
        // TODO: P3 — query JPA repository by factId + tenantId
        throw new UnsupportedOperationException("not implemented — deferred to P3");
    }
}
