package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.DetectionProfileJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionProfileJpaRepository
        extends JpaRepository<DetectionProfileJpaEntity, UUID> {

    Optional<DetectionProfileJpaEntity> findByActiveTrue();

    Optional<DetectionProfileJpaEntity> findByVersion(int version);

    List<DetectionProfileJpaEntity> findAllByActiveTrue();
}
