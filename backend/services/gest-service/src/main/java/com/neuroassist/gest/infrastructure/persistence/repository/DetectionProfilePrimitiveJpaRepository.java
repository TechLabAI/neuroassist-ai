package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.DetectionProfilePrimitiveJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionProfilePrimitiveJpaRepository
        extends JpaRepository<DetectionProfilePrimitiveJpaEntity, UUID> {

    List<DetectionProfilePrimitiveJpaEntity> findByProfileId(UUID profileId);
}
