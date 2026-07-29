package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.infrastructure.persistence.repository.DetectionProfileJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionProfileRepositoryAdapter implements DetectionProfileRepositoryPort {

    private final DetectionProfileJpaRepository repository;

    @Override
    public DetectionProfile save(DetectionProfile entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<DetectionProfile> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<DetectionProfile> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
