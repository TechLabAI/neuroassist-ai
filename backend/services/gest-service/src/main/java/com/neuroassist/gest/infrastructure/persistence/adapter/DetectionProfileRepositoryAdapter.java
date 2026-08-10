package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.infrastructure.persistence.entity.DetectionProfileJpaEntity;
import com.neuroassist.gest.infrastructure.persistence.repository.DetectionProfileJpaRepository;
import java.util.Collections;
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
        DetectionProfileJpaEntity jpa = DetectionProfileJpaEntity.builder()
                .id(entity.id())
                .version(entity.version())
                .active(entity.active())
                .contractVersion(entity.contractVersion())
                .publishedAt(entity.publishedAt())
                .build();
        return toDomain(repository.save(jpa));
    }

    @Override
    public Optional<DetectionProfile> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<DetectionProfile> findByTenantId(String tenantId) {
        return Collections.emptyList();
    }

    @Override
    public Optional<DetectionProfile> findActive() {
        return repository.findByActiveTrue().map(this::toDomain);
    }

    @Override
    public Optional<DetectionProfile> findByVersion(int version) {
        return repository.findByVersion(version).map(this::toDomain);
    }

    @Override
    public void deactivateAll() {
        List<DetectionProfileJpaEntity> active = repository.findAllByActiveTrue();
        for (DetectionProfileJpaEntity e : active) {
            e.setActive(false);
        }
        repository.saveAll(active);
    }

    private DetectionProfile toDomain(DetectionProfileJpaEntity e) {
        return new DetectionProfile(
                e.getId(),
                e.getVersion(),
                e.isActive(),
                e.getContractVersion(),
                e.getPublishedAt());
    }
}
