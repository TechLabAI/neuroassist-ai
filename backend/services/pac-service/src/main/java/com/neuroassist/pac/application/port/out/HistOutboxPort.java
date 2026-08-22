package com.neuroassist.pac.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port — the durable half of {@link HistFactPort}.
 *
 * <p>Until now nothing ever read {@code status = 'pending'}. The adapter's comment said "retry will
 * handle it later" and no retry existed, so a fact written while hist-service was down stayed in
 * the table forever (defect C-44). This port is what makes the word "outbox" true.
 */
public interface HistOutboxPort {

    /** Oldest pending entries first, so a burst does not starve what has been waiting longest. */
    List<PendingFact> findPending(int limit);

    void markSent(UUID id);

    /** Records a failed attempt; the entry stays {@code pending}. */
    void markRetried(UUID id);

    /**
     * Gives up on an entry after too many attempts, moving it to {@code failed}.
     *
     * <p>Terminal on purpose: an entry retried forever is indistinguishable from a healthy queue,
     * and hides a fact that will never be delivered behind a number that keeps growing.
     */
    void markFailed(UUID id);

    record PendingFact(UUID id, String eventType, String payloadJson, int retryCount) {}
}
