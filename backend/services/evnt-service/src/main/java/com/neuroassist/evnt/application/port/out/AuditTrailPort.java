package com.neuroassist.evnt.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit trail, served by hist-service.
 *
 * <p>The previous signature was {@code append(String action, String entityId, String details)} —
 * three loose strings, which was enough while the only implementation was {@code
 * AuditTrailNoOpAdapter}. hist-service needs a structured fact: an idempotency key, a type from a
 * controlled vocabulary, a typed subject, an actor, and a payload.
 *
 * <p><b>Not on the critical path.</b> An audit write must never fail the business operation that
 * produced it (AUD-10) — implementations absorb failures and log them.
 */
public interface AuditTrailPort {

    void record(AuditFact fact);

    /**
     * @param factId idempotency key — the same value replayed returns the original record rather
     *     than appending a second one, so it must be minted once and reused across retries
     * @param factType from {@code hist.fact-types.allowed}; anything else is rejected with 422
     * @param subjectType one of {@code paciente | comando | cuidador | dispositivo}
     * @param actorCaregiverId the person the fact is attributed to — still recorded even though the
     *     call itself is authenticated as evnt-service (service-identity.md §4)
     * @param payload must not carry biometric keys ({@code landmarks}, {@code image}, {@code
     *     frame}, {@code video}, {@code embedding}) — hist-service rejects those with 422
     */
    record AuditFact(
            UUID factId,
            String factType,
            String subjectType,
            UUID subjectId,
            UUID actorCaregiverId,
            String actorDisplayName,
            Map<String, Object> payload,
            Instant occurredAt) {}
}
