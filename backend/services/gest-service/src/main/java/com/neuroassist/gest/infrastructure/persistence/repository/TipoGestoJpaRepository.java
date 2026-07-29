package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoGestoJpaRepository extends JpaRepository<TipoGestoJpaEntity, UUID> {

    List<TipoGestoJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
