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
        UUID correctsRecordId,

        /**
         * Derived read-time flag — always {@code false} at write time. Set to {@code true} when the
         * per-patient encryption key has been erased (LGPD crypto-shredding, P10 / HIST-024),
         * making the {@code payload} unreadable. The hash chain structure remains intact.
         */
        boolean erased,

        /**
         * The patient this fact concerns, used to scope caregiver reads (C-16).
         *
         * <p>Denormalized on purpose. The scope cannot be derived at query time: for a command fact
         * the {@link #subject()} is the command, and the patient id lives inside the {@code
         * payload}, which is encrypted at rest. Producers supply it; hist-service falls back to the
         * subject id when the subject already <i>is</i> a patient.
         *
         * <p>{@code null} means the fact has no patient scope and is visible only with {@code
         * hist:read:all}.
         *
         * <p><b>Not part of the hash pre-image.</b> {@code Sha256HashChainAdapter#canonicalize}
         * builds an explicit named map; adding this field there would invalidate every record
         * already written. It is routing metadata, not attested content.
         */
        UUID scopePacienteId) {}
