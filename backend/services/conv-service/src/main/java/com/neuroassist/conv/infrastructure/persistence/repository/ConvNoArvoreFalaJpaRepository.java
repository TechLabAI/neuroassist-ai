package com.neuroassist.conv.infrastructure.persistence.repository;

import com.neuroassist.conv.infrastructure.persistence.entity.ConvNoArvoreFalaJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConvNoArvoreFalaJpaRepository
        extends JpaRepository<ConvNoArvoreFalaJpaEntity, UUID> {

    List<ConvNoArvoreFalaJpaEntity> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
