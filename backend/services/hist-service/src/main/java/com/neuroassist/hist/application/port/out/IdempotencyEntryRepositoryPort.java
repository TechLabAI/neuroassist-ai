package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.IdempotencyEntry;
import java.util.Optional;

/**
 * Outbound port — low-level idempotency registry persistence (P3, HIST-010). Consumed by the
 * higher-level {@link IdempotencyPort} adapter. P1 stub: no implementation yet.
 */
public interface IdempotencyEntryRepositoryPort {

    /** Persists a new idempotency entry. */
    IdempotencyEntry save(IdempotencyEntry entry, String tenantId);

    /**
     * Looks up an idempotency entry by (factId, tenantId). Returns empty if the factId was never
     * ingested in this tenant.
     */
    Optional<IdempotencyEntry> findByFactIdAndTenantId(java.util.UUID factId, String tenantId);
}
