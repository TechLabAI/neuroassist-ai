package com.neuroassist.hist.api.dto;

import java.time.Instant;

/** Response body for {@code GET /api/v1/hist/history/verify}. */
public record ChainVerificationResponse(
        /** Canonical stream key: {@code "<subjectType>:<subjectId>"}. */
        String streamKey,

        /** Verification outcome: {@code intact | broken | empty | partially_erased}. */
        String status,

        /** Number of records whose hash was successfully verified. */
        int recordsVerified,

        /** Number of records whose payload was LGPD-erased and hash re-computation was skipped. */
        int erasedCount,

        /**
         * First {@code sequenceNo} where verification failed. Null when status is {@code intact},
         * {@code empty}, or {@code partially_erased}.
         */
        Long brokenAtSequenceNo,

        /** Wall-clock time when this verification was computed. */
        Instant verifiedAt) {}
