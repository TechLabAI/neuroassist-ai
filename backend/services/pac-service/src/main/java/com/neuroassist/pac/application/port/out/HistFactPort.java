package com.neuroassist.pac.application.port.out;

import com.neuroassist.pac.domain.model.HistFact;

/**
 * Outbound port — HistFactPort (INT-PAC-004).
 *
 * <p>Records domain facts to the hist-service. Implementations must be resilient: if hist-service
 * is unavailable, the fact is persisted in the local {@code hist_outbox} table (within the caller's
 * {@code @Transactional} boundary) and retried asynchronously.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>This method NEVER throws — it absorbs any delivery failure and falls back to outbox.
 *   <li>The outbox write is part of the caller's transaction → committed atomically with the
 *       lifecycle change (resilient recording, no rollback on HIST failure).
 *   <li>Implementations must NOT declare {@code @Transactional} (RULE-03).
 *   <li>{@code @FeignClient} must stay in {@code infrastructure.clients} (RULE-06).
 * </ul>
 *
 * <p>Domain facts covered: {@code comando.published}, {@code comando.recalibrated}, {@code
 * comando.retired}.
 */
public interface HistFactPort {

    /**
     * Records a domain fact. Attempts direct delivery to hist-service; on failure writes to the
     * local outbox for retry. Never propagates exceptions to the caller.
     *
     * @param fact the domain fact to record (must not be null)
     */
    void record(HistFact fact);
}
