package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.DetectionProfileJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionProfileJpaRepository
        extends JpaRepository<DetectionProfileJpaEntity, UUID> {

    List<DetectionProfileJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
