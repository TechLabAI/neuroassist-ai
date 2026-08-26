package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.AuditRecord;

/**
 * Outbound port — write-once insert of a single {@link AuditRecord} into the tenant ledger.
 * Implementations MUST NOT expose any UPDATE or DELETE operation (ADR-004). {@code
 * EntityManager.persist()} is the canonical write method (never {@code save()}).
 */
public interface AuditLedgerPort {

    /**
     * Inserts a new audit record atomically within the current transaction. Must be called with an
     * active tenant context ({@link
     * com.neuroassist.hist.infrastructure.security.TenantContextHolder}).
     *
     * @param record the fully-populated domain record (id, hashes, sequenceNo set by caller)
     * @param tenantId the schema identifier for schema routing
     * @return the persisted record (identical to input; included for call-site clarity)
     */
    AuditRecord insert(AuditRecord record, String tenantId);
}
