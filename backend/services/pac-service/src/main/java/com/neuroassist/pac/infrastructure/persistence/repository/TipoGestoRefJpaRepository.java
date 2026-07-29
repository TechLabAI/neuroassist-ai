package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.TipoGestoRefJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoGestoRefJpaRepository extends JpaRepository<TipoGestoRefJpaEntity, UUID> {

    List<TipoGestoRefJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
