package com.neuroassist.hist.application.port.in;

import com.neuroassist.hist.domain.model.AuditRecord;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port — fetch a single audit record by HIST id. Exposed at {@code GET
 * /api/v1/hist/audit-facts/{id}}. Requires {@code hist:read} scope.
 */
public interface AuditRecordQueryUseCase {

    /**
     * Finds an audit record by its HIST-owned id within the caller's tenant. Cross-tenant lookup
     * always returns empty (never leaks cross-tenant existence).
     *
     * @param id the HIST-owned audit record id
     * @param tenantId the caller's tenant (from JWT {@code tenant} claim)
     * @return the record, or empty if not found in this tenant
     */
    Optional<AuditRecord> findById(UUID id, String tenantId);
}
