package com.neuroassist.conv.application.port.out;

import com.neuroassist.conv.domain.model.ConvAuditOutbox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConvAuditOutboxRepositoryPort {

    ConvAuditOutbox save(ConvAuditOutbox entity);

    Optional<ConvAuditOutbox> findById(UUID id);

    List<ConvAuditOutbox> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
