package com.neuroassist.conv.infrastructure.persistence.adapter;

import com.neuroassist.conv.application.port.out.ConvAuditOutboxRepositoryPort;
import com.neuroassist.conv.domain.model.ConvAuditOutbox;
import com.neuroassist.conv.infrastructure.persistence.repository.ConvAuditOutboxJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConvAuditOutboxRepositoryAdapter implements ConvAuditOutboxRepositoryPort {

    private final ConvAuditOutboxJpaRepository repository;

    @Override
    public ConvAuditOutbox save(ConvAuditOutbox entity) {
        // TODO[SCAFFOLD]: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<ConvAuditOutbox> findById(UUID id) {
        // TODO[SCAFFOLD]: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<ConvAuditOutbox> findByTenantId(String tenantId) {
        // TODO[SCAFFOLD]: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
