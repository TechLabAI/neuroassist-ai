package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.PacienteJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PacienteJpaRepository extends JpaRepository<PacienteJpaEntity, UUID> {

    List<PacienteJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
