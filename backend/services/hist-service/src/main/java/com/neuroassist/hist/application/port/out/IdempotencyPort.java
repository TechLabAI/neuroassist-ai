package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.IdempotencyEntry;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — idempotency registry for audit fact ingestion (ADR-005). Implementations
 * check/register {@code (tenantId, factId)} pairs atomically.
 */
public interface IdempotencyPort {

    /**
     * Looks up an existing idempotency entry for the given (tenant, factId) pair. Returns empty if
     * this factId has never been successfully ingested in this tenant.
     *
     * @param factId producer-minted idempotency key
     * @param tenantId tenant schema identifier
     * @return the existing entry, or empty if the factId is new
     */
    Optional<IdempotencyEntry> findByFactId(UUID factId, String tenantId);

    /**
     * Records a new idempotency entry after a successful append. Must be called within the same
     * transaction as the ledger insert.
     *
     * @param entry the entry to persist
     * @param tenantId tenant schema identifier (for schema routing)
     */
    void record(IdempotencyEntry entry, String tenantId);
}
