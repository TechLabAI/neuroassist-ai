package com.neuroassist.pac.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port — a durable record of the intent to propagate a caregiver-patient link to the ACS
 * directory (E2E-015 / defect C-30).
 *
 * <p>Until now the PAC wrote {@code paciente_cuidador} and stopped there, while the ACS directory —
 * the thing EVNT consults to decide who receives an alarm — read a different table in a different
 * schema. Nothing connected them, so every patient created through the PAC had an empty directory
 * and an alarm with no destination.
 *
 * <p><b>Why an outbox and not a Feign call inside {@code create()}.</b> Calling the ACS before the
 * commit makes patient creation fail whenever the ACS is down, in a path that has no reason to be
 * fail-closed. Calling it after the commit reintroduces exactly the divergence this fixes, only
 * with a shorter window. The entry is written inside the caller's transaction, so the local link
 * and the intent to propagate it are committed together or not at all.
 */
public interface DirectoryOutboxPort {

    /** Records the intent to create the link in the ACS directory. */
    void enqueueLink(UUID pacienteId, UUID cuidadorId);

    /** Records the intent to revoke the link in the ACS directory. */
    void enqueueUnlink(UUID pacienteId, UUID cuidadorId);

    /**
     * Oldest entries first, bounded.
     *
     * <p>The order is load-bearing, not a nicety: a LINK followed by an UNLINK of the same pair
     * delivered out of order leaves the directory holding a link the PAC has already removed.
     */
    List<PendingEntry> findPending(int limit);

    void markSent(UUID id);

    /** Records a failed attempt; the entry stays {@code pending}. */
    void markRetried(UUID id, String error);

    /**
     * Gives up on an entry, moving it to {@code failed} with the reason that ended it.
     *
     * <p>Terminal on purpose, and terminal with a cause: an entry retried forever looks like a busy
     * queue, and an entry that failed without a recorded reason cannot be triaged later.
     */
    void markFailed(UUID id, String error);

    /**
     * @param operation {@code LINK} or {@code UNLINK}
     */
    record PendingEntry(
            UUID id, String operation, UUID pacienteId, UUID cuidadorId, int retryCount) {}
}
