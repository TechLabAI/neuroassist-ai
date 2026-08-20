package com.neuroassist.hist.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Value object — idempotency registry entry keyed by {@code (tenantId, factId)}. Persisted
 * atomically with the corresponding {@link AuditRecord} so that replays of the same {@code factId}
 * return the original record without a second append (ADR-005). Zero Spring/JPA imports (RULE-01,
 * RULE-02).
 */
public record IdempotencyEntry(
        /** Producer-minted fact identifier — idempotency key. */
        UUID factId,

        /** Tenant schema identifier. */
        String tenantId,

        /** HIST-owned id of the {@link AuditRecord} that was persisted for this factId. */
        UUID auditRecordId,

        /** When the entry was first recorded (HIST durable clock). */
        Instant recordedAt) {}
