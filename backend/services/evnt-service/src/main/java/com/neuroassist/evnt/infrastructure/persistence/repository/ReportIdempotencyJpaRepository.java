package com.neuroassist.evnt.infrastructure.persistence.repository;

import com.neuroassist.evnt.infrastructure.persistence.entity.ReportIdempotencyJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportIdempotencyJpaRepository
        extends JpaRepository<ReportIdempotencyJpaEntity, UUID> {

    List<ReportIdempotencyJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
