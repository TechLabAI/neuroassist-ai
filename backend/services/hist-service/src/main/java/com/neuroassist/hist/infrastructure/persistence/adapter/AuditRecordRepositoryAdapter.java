package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.infrastructure.persistence.repository.AuditRecordJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuditRecordRepositoryAdapter implements AuditRecordRepositoryPort {

    private final AuditRecordJpaRepository repository;

    @Override
    public AuditRecord save(AuditRecord entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<AuditRecord> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<AuditRecord> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
