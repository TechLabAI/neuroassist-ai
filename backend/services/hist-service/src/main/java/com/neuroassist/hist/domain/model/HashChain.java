package com.neuroassist.hist.domain.model;

/**
 * Value object representing the hash-chain position of one record within a stream. Computed by
 * {@code HashChainPort} and consumed by {@code RecordAuditFactService} (P2). Zero Spring/JPA
 * imports (RULE-01, RULE-02).
 */
public record HashChain(
        /** Canonical stream key: {@code "<subjectType>:<subjectId>"}. */
        String streamKey,

        /** Monotone sequence position within the stream (1-based). */
        long sequenceNo,

        /** SHA-256 of the previous record (all-zeros hex for the genesis record). */
        String prevHash,

        /** SHA-256 of {@code prevHash ‖ canonical(record)} for this record. */
        String recordHash) {

    /** Hex-encoded zero hash used for the genesis record in a new stream. */
    public static final String GENESIS_PREV_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";
}
