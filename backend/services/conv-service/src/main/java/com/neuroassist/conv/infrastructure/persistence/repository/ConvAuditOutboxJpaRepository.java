package com.neuroassist.conv.infrastructure.persistence.repository;

import com.neuroassist.conv.infrastructure.persistence.entity.ConvAuditOutboxJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConvAuditOutboxJpaRepository
        extends JpaRepository<ConvAuditOutboxJpaEntity, UUID> {

    List<ConvAuditOutboxJpaEntity> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
