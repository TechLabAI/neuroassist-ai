package com.neuroassist.edge.infrastructure.persistence.repository;

import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEnrollmentJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoEnrollmentJpaRepository
        extends JpaRepository<DispositivoEnrollmentJpaEntity, UUID> {

    List<DispositivoEnrollmentJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
