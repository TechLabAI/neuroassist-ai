package com.neuroassist.edge.infrastructure.persistence.repository;

import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoCredentialJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoCredentialJpaRepository
        extends JpaRepository<DispositivoCredentialJpaEntity, UUID> {

    List<DispositivoCredentialJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
