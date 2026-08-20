package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.infrastructure.persistence.repository.AuditRecordJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter — JPA implementation of {@link AuditRecordRepositoryPort}.
 *
 * <p>P2 stubs: method signatures updated to match the port; real JPA mapping implemented in P3.
 */
@Component
@RequiredArgsConstructor
public class AuditRecordRepositoryAdapter implements AuditRecordRepositoryPort {

    private final AuditRecordJpaRepository repository;

    @Override
    public AuditRecord save(AuditRecord entity) {
        // TODO P3: map domain model → JPA entity → persist → map back
        throw new UnsupportedOperationException(
                "AuditRecordRepositoryAdapter.save — implemented in P3");
    }

    @Override
    public Optional<AuditRecord> findById(UUID id, String tenantId) {
        // TODO P3: map JPA entity → domain model; filter by tenantId for isolation
        throw new UnsupportedOperationException(
                "AuditRecordRepositoryAdapter.findById — implemented in P3");
    }

    @Override
    public List<AuditRecord> findByStreamKeyOrderedBySequenceAsc(
            String streamKey, String tenantId) {
        // TODO P3: JPA query ORDER BY sequenceNo ASC WHERE streamKey=? AND tenantId=?
        throw new UnsupportedOperationException(
                "AuditRecordRepositoryAdapter.findByStreamKeyOrderedBySequenceAsc — implemented in P3");
    }

    @Override
    public FilteredPage findFiltered(AuditRecordFilter filter, String tenantId) {
        // TODO P3: JPA Specification or JPQL with dynamic filter + pagination
        throw new UnsupportedOperationException(
                "AuditRecordRepositoryAdapter.findFiltered — implemented in P3");
    }
}
