package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.ActorRefJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActorRefJpaRepository extends JpaRepository<ActorRefJpaEntity, UUID> {

    List<ActorRefJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
