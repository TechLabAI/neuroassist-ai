package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.DetectionProfilePrimitiveJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionProfilePrimitiveJpaRepository
        extends JpaRepository<DetectionProfilePrimitiveJpaEntity, UUID> {

    // Multi-tenancy is via schema routing (search_path), not a tenant_id column.

    // TODO P3: query snapshot members by profile
    List<DetectionProfilePrimitiveJpaEntity> findByDetectionProfileId(UUID detectionProfileId);
}
