package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.FactTypeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FactTypeJpaRepository extends JpaRepository<FactTypeJpaEntity, UUID> {

    List<FactTypeJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
