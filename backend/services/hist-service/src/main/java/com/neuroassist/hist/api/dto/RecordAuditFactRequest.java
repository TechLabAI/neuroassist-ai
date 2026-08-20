package com.neuroassist.hist.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/hist/audit-facts}. All fields except {@code correctsFactId}
 * are required.
 */
public record RecordAuditFactRequest(

        /**
         * Producer-minted idempotency key. Unique per tenant; same factId submitted N times
         * produces exactly one ledger record (ADR-005).
         */
        @NotNull UUID factId,

        /**
         * Dot-notation fact type from the controlled vocabulary (v1). Unknown values are rejected
         * with HTTP 422.
         */
        @NotBlank String factType,

        /** The entity this fact is about. */
        @NotNull @Valid SubjectRefDto subject,

        /** Caregiver snapshot at ingestion time. */
        @NotNull @Valid ActorRefDto actor,

        /**
         * Structured JSON payload. Must not contain biometric/image keys ({@code landmarks, image,
         * frame, video, embedding}) — rejected with HTTP 422.
         */
        @NotNull Map<String, Object> payload,

        /** Producer clock. Must not be more than 5 minutes in the future. */
        @NotNull Instant occurredAt,

        /**
         * Optional: if this record corrects a prior fact, the factId of that fact. The prior fact
         * must exist in the same tenant (resolved by the service in P2).
         */
        UUID correctsFactId,

        /**
         * Optional: client-supplied tenant identifier. When present, must match the {@code
         * tenant_id} claim from the JWT — mismatch results in HTTP 403 tenant-mismatch.
         */
        String empresaId) {}
