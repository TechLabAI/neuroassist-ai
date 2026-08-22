package com.neuroassist.acs.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound port — HistAuditPort. Communicates with hist-service to record audit facts. Timeout: 1s
 * (best-effort, fire-and-forget — never blocks main transaction). Zero Spring/JPA imports (RULE-01,
 * RULE-02, RULE-06).
 */
public interface HistAuditPort {

    /**
     * Records an audit fact asynchronously. This call is fire-and-forget: failures are logged but
     * do not propagate.
     *
     * @param fact the audit fact to record
     */
    void recordFact(AuditFact fact);

    /**
     * Immutable audit fact value object.
     *
     * @param fact audit event type (e.g. "LOGIN_SUCCESS", "CAREGIVER_CREATED")
     * @param empresaId tenant/empresa UUID context
     * @param actorId UUID of the actor who performed the action (null for system events)
     * @param correlationId request correlation ID from MDC
     * @param occurredAt timestamp when the event occurred
     * @param payload additional event-specific data (must not contain credentials)
     */
    record AuditFact(
            String fact,
            UUID empresaId,
            UUID actorId,
            String correlationId,
            Instant occurredAt,
            Map<String, Object> payload) {}
}
