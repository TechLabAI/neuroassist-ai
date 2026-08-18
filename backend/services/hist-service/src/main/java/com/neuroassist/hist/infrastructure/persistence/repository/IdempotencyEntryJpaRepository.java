package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.IdempotencyEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface IdempotencyEntryJpaRepository extends JpaRepository<IdempotencyEntryJpaEntity, UUID> {

    List<IdempotencyEntryJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
