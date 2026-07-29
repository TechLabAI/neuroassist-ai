package com.neuroassist.edge.infrastructure.persistence.repository;

import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEdgeJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoEdgeJpaRepository
        extends JpaRepository<DispositivoEdgeJpaEntity, UUID> {

    List<DispositivoEdgeJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
