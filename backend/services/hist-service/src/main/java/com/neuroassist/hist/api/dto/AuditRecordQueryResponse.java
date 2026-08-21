package com.neuroassist.hist.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/hist/audit-facts/{id}}. Identical shape to {@link
 * RecordAuditFactResponse}; kept as a separate type for future divergence (e.g. additional
 * read-side projections in P4).
 */
public record AuditRecordQueryResponse(
        UUID id,
        UUID factId,
        String factType,
        SubjectRefDto subject,
        ActorRefDto actor,
        Map<String, Object> payload,
        Instant occurredAt,
        Instant recordedAt,
        String streamKey,
        long sequenceNo,
        String prevHash,
        String recordHash,
        UUID correctsRecordId) {}
