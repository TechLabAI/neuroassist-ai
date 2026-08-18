package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.HashChainJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface HashChainJpaRepository extends JpaRepository<HashChainJpaEntity, UUID> {

    List<HashChainJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
