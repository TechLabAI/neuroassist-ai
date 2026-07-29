package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.ComandoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComandoJpaRepository extends JpaRepository<ComandoJpaEntity, UUID> {

    List<ComandoJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
