package com.neuroassist.conv.infrastructure.persistence.repository;

import com.neuroassist.conv.infrastructure.persistence.entity.ConvArvoreFalaJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConvArvoreFalaJpaRepository extends JpaRepository<ConvArvoreFalaJpaEntity, UUID> {

    List<ConvArvoreFalaJpaEntity> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
