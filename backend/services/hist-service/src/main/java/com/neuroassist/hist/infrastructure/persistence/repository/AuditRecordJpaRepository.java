package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.AuditRecordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditRecordJpaRepository extends JpaRepository<AuditRecordJpaEntity, UUID> {

    List<AuditRecordJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
