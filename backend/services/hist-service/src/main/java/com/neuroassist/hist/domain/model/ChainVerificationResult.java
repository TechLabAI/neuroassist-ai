package com.neuroassist.hist.domain.model;

import java.time.Instant;

/**
 * Result of a hash-chain verification operation for one (tenant, subject) stream. Returned by
 * {@code ChainVerificationUseCase.verifyStream()} (P2 implementation). Zero Spring/JPA imports
 * (RULE-01, RULE-02).
 */
public record ChainVerificationResult(
        /** Canonical stream key: {@code "<subjectType>:<subjectId>"}. */
        String streamKey,

        /** Verification outcome: {@code intact}, {@code broken}, {@code empty}, or {@code partially_erased}. */
        ChainStatus status,

        /** Number of records whose hash was successfully verified. 0 when status is {@code empty}. */
        int recordsVerified,

        /**
         * Number of records whose payload was erased (LGPD crypto-shredding) and whose hash
         * re-computation was skipped. 0 when no erasure has occurred.
         */
        int erasedCount,

        /**
         * First {@code sequenceNo} where the hash or linkage check failed. Null when status is
         * {@code intact}, {@code empty}, or {@code partially_erased}.
         */
        Long brokenAtSequenceNo,

        /** Wall-clock time when this verification was computed (HIST durable clock). */
        Instant verifiedAt) {}
