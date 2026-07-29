package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoTransitionJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoGestoTransitionJpaRepository
        extends JpaRepository<TipoGestoTransitionJpaEntity, UUID> {

    List<TipoGestoTransitionJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
