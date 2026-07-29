package com.neuroassist.edge.infrastructure.persistence.repository;

import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEdgeTransitionJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoEdgeTransitionJpaRepository
        extends JpaRepository<DispositivoEdgeTransitionJpaEntity, UUID> {

    List<DispositivoEdgeTransitionJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
