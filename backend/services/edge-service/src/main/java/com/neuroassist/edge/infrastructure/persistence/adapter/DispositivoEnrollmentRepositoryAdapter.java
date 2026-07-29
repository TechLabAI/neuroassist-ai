package com.neuroassist.edge.infrastructure.persistence.adapter;

import com.neuroassist.edge.application.port.out.DispositivoEnrollmentRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoEnrollment;
import com.neuroassist.edge.infrastructure.persistence.repository.DispositivoEnrollmentJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DispositivoEnrollmentRepositoryAdapter implements DispositivoEnrollmentRepositoryPort {

    private final DispositivoEnrollmentJpaRepository repository;

    @Override
    public DispositivoEnrollment save(DispositivoEnrollment entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<DispositivoEnrollment> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<DispositivoEnrollment> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
