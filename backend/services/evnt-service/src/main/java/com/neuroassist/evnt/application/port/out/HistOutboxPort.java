package com.neuroassist.evnt.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port — the durable half of {@link AuditTrailPort} (E2E-017).
 *
 * <p>Fail-open without durability is the worst of both worlds: the caller is told nothing, and the
 * fact is gone. {@code AuditTrailFeignAdapter} logged exactly that — {@code "was NOT recorded and
 * is lost — evnt-service has no outbox"}. This port is what makes the loss impossible instead of
 * merely visible.
 *
 * <p>The entry is written inside the caller's transaction, so an audit fact exists if and only if
 * the event that produced it exists.
 */
public interface HistOutboxPort {

    /**
     * Stores a fact for later delivery.
     *
     * <p>Idempotent on {@code factId}: enqueuing a fact already in the table is a no-op. The direct
     * attempt and the outbox write are two paths for one fact, and the fact must never be queued
     * twice — hist-service would deduplicate it, which would hide the double-queue rather than
     * report it.
     */
    void enqueue(UUID factId, String factType, String payloadJson);

    /** Oldest pending entries first, bounded, so a burst does not starve what waited longest. */
    List<PendingFact> findPending(int limit);

    void markSent(UUID id);

    /** Records a failed attempt; the entry stays {@code pending}. */
    void markRetried(UUID id, String error);

    /**
     * Gives up after too many attempts, with the reason that ended it.
     *
     * <p>Terminal on purpose: an entry retried forever is indistinguishable from a healthy queue,
     * and hides a fact that will never be delivered behind a number that keeps growing.
     */
    void markFailed(UUID id, String error);

    record PendingFact(UUID id, UUID factId, String factType, String payloadJson, int retryCount) {}
}
