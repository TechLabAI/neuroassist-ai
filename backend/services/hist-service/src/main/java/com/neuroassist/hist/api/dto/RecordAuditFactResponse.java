package com.neuroassist.hist.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/hist/audit-facts} (201 Created or 200 OK replay) and {@code
 * GET /api/v1/hist/audit-facts/{id}} (200 OK).
 */
public record RecordAuditFactResponse(

        /** HIST-owned surrogate identifier (uuid_generate_v7). */
        UUID id,

        /** Producer-minted idempotency key echoed back. */
        UUID factId,

        /** Dot-notation fact type code. */
        String factType,

        /** Subject reference. */
        SubjectRefDto subject,

        /** Caregiver snapshot. */
        ActorRefDto actor,

        /** Structured payload as submitted (never contains biometric data). */
        Map<String, Object> payload,

        /** Producer clock. */
        Instant occurredAt,

        /** HIST durable clock — authoritative ordering. */
        Instant recordedAt,

        /** Canonical stream key: {@code "<subjectType>:<subjectId>"}. */
        String streamKey,

        /** Monotone position of this record within its stream (1-based). */
        long sequenceNo,

        /** SHA-256 of the previous record in the stream (64-hex). */
        String prevHash,

        /** SHA-256 of {@code prevHash ‖ canonical(record)} for this record (64-hex). */
        String recordHash,

        /**
         * If this record corrects a prior record, the HIST id of the corrected record. Null
         * otherwise.
         */
        UUID correctsRecordId) {}
