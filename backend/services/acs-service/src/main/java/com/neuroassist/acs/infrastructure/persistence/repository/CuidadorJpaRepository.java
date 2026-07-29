package com.neuroassist.acs.infrastructure.persistence.repository;

import com.neuroassist.acs.infrastructure.persistence.entity.CuidadorJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuidadorJpaRepository extends JpaRepository<CuidadorJpaEntity, UUID> {

    List<CuidadorJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
