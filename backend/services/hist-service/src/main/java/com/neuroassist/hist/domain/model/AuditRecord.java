package com.neuroassist.hist.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Core aggregate — an immutable audit record in the ledger.
 *
 * <p>Invariants (ADR-004, ADR-005, ADR-006):
 *
 * <ul>
 *   <li>Write-once: once persisted, no field is ever mutated.
 *   <li>Idempotent on {@code factId}: same factId → same record returned (P2).
 *   <li>Hash-chained: {@code recordHash = sha256(prevHash ‖ canonical(record))} (P2).
 *   <li>Tenant-scoped: {@code tenantId} comes from the JWT claim, never from request body.
 * </ul>
 *
 * Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public record AuditRecord(
        /** HIST-owned surrogate identifier (uuid_generate_v7 at insert). */
        UUID id,

        /** Tenant schema identifier — derived from JWT {@code tenant} claim. */
        String tenantId,

        /** Producer-minted idempotency key — unique per tenant. */
        UUID factId,

        /** Dot-notation fact type code from the controlled vocabulary (v1). */
        String factType,

        /** The entity this fact is about (paciente, comando, cuidador, dispositivo). */
        SubjectRef subject,

        /** Caregiver snapshot at ingestion time (denormalized — never re-fetched). */
        ActorRef actor,

        /**
         * Structured JSON payload as submitted by the producer. Must NOT contain biometric/image
         * keys ({@code landmarks, image, frame, video, embedding}).
         */
        Map<String, Object> payload,

        /** Producer clock. Must not be more than 5 minutes in the future (validated at ingest). */
        Instant occurredAt,

        /** HIST durable clock — set by the service, authoritative ordering. */
        Instant recordedAt,

        /** Canonical stream key: {@code "<subjectType>:<subjectId>"}. */
        String streamKey,

        /** Monotone position of this record within its (tenant, streamKey) stream (1-based). */
        long sequenceNo,

        /**
         * SHA-256 hash of the previous record in this stream (genesis record uses all-zeros hex).
         */
        String prevHash,

        /** SHA-256 of {@code prevHash ‖ canonical(this record)} (ADR-006). */
        String recordHash,

        /**
         * If this record corrects a prior fact, the id of the corrected {@link AuditRecord}. Null
         * otherwise.
         */
        UUID correctsRecordId) {}
