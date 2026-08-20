package com.neuroassist.hist.domain.model;

import java.time.Instant;

/**
 * Value object — cached tip of a hash-chain stream {@code (tenant, streamKey)}. Used to derive the
 * {@code prevHash} and {@code sequenceNo} for the next append. Persisted as a mutable cache row
 * (unlike {@link AuditRecord} which is immutable). Locked pessimistically by {@code
 * StreamHeadPort.lockAndFetchTip()} before each append (P3). Zero Spring/JPA imports (RULE-01,
 * RULE-02).
 */
public record StreamHead(
        /** Canonical stream key: {@code "<subjectType>:<subjectId>"}. */
        String streamKey,

        /** Tenant schema identifier. */
        String tenantId,

        /** Sequence number of the last appended record in this stream. */
        long currentSequenceNo,

        /** SHA-256 hash of the last appended record ({@code recordHash}). */
        String currentHash,

        /** {@code recordedAt} of the last appended record. */
        Instant lastRecordedAt) {}
