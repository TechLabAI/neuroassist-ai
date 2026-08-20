package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.HashChainJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HashChainJpaRepository extends JpaRepository<HashChainJpaEntity, UUID> {

    List<HashChainJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
