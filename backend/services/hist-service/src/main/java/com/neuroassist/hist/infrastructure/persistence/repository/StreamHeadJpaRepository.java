package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.StreamHeadJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StreamHeadJpaRepository extends JpaRepository<StreamHeadJpaEntity, UUID> {

    List<StreamHeadJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
