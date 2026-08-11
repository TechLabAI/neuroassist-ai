package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.infrastructure.persistence.entity.DetectionProfilePrimitiveJpaEntity;
import com.neuroassist.gest.infrastructure.persistence.repository.DetectionProfilePrimitiveJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link DetectionProfilePrimitiveRepositoryPort}. Records are immutable after
 * creation — {@code saveAll} is the only write operation. Schema routing via search_path is
 * transparent. Implemented in P3 (GEST-012).
 */
@Component
@RequiredArgsConstructor
public class DetectionProfilePrimitiveRepositoryAdapter
        implements DetectionProfilePrimitiveRepositoryPort {

    private final DetectionProfilePrimitiveJpaRepository repository;

    @Override
    public List<DetectionProfilePrimitive> saveAll(List<DetectionProfilePrimitive> primitives) {
        List<DetectionProfilePrimitiveJpaEntity> entities =
                primitives.stream().map(this::toEntity).toList();
        List<DetectionProfilePrimitiveJpaEntity> saved = repository.saveAll(entities);
        return saved.stream().map(this::toDomain).toList();
    }

    @Override
    public List<DetectionProfilePrimitive> findByProfileId(UUID profileId) {
        return repository.findByDetectionProfileId(profileId).stream().map(this::toDomain).toList();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Mapping helpers — domain ↔ JPA entity
    // ────────────────────────────────────────────────────────────────────────

    private DetectionProfilePrimitiveJpaEntity toEntity(DetectionProfilePrimitive p) {
        return DetectionProfilePrimitiveJpaEntity.builder()
                .id(p.id())
                .detectionProfileId(p.profileId())
                .tipoGestoId(p.tipoGestoId())
                .nomeSnapshot(p.nomeSnapshot())
                .engineFeatureRef(p.engineFeatureRef())
                .build();
    }

    private DetectionProfilePrimitive toDomain(DetectionProfilePrimitiveJpaEntity entity) {
        return new DetectionProfilePrimitive(
                entity.getId(),
                entity.getDetectionProfileId(),
                entity.getTipoGestoId(),
                entity.getNomeSnapshot(),
                entity.getEngineFeatureRef());
    }
}
