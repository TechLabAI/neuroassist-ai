package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoTransitionJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoGestoTransitionJpaRepository
        extends JpaRepository<TipoGestoTransitionJpaEntity, UUID> {

    // Multi-tenancy is via schema routing (search_path), not a tenant_id column.

    // TODO P3: add query by tipoGestoId (e.g. findByTipoGestoId)
    List<TipoGestoTransitionJpaEntity> findByTipoGestoId(UUID tipoGestoId);
}
