package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.NotificationDispatchRepositoryPort;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.infrastructure.persistence.repository.NotificationDispatchJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDispatchRepositoryAdapter implements NotificationDispatchRepositoryPort {

    private final NotificationDispatchJpaRepository repository;

    @Override
    public NotificationDispatch save(NotificationDispatch entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<NotificationDispatch> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<NotificationDispatch> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
