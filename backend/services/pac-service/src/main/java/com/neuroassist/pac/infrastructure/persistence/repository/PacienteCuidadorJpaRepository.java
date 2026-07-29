package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.PacienteCuidadorJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PacienteCuidadorJpaRepository
        extends JpaRepository<PacienteCuidadorJpaEntity, UUID> {

    List<PacienteCuidadorJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
