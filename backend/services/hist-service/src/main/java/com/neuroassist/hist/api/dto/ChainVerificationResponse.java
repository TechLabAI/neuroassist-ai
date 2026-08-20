package com.neuroassist.hist.api.dto;

import java.time.Instant;

/** Response body for {@code GET /api/v1/hist/history/verify}. */
public record ChainVerificationResponse(
        /** Canonical stream key: {@code "<subjectType>:<subjectId>"}. */
        String streamKey,

        /** Verification outcome: {@code intact | broken | empty}. */
        String status,

        /** Number of records whose hash was verified. */
        int recordsVerified,

        /**
         * First {@code sequenceNo} where verification failed. Null when status is {@code intact} or
         * {@code empty}.
         */
        Long brokenAtSequenceNo,

        /** Wall-clock time when this verification was computed. */
        Instant verifiedAt) {}
