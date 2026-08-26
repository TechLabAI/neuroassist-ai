package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.AuditRecord;

/**
 * Outbound port — SHA-256 hash-chain computation (ADR-006). Implementations compute {@code
 * sha256(prevHash ‖ canonical(record))} using JCS / RFC 8785 canonical JSON serialisation for
 * determinism.
 */
public interface HashChainPort {

    /**
     * Computes the {@code recordHash} for a given record positioned after {@code prevHash}.
     *
     * @param prevHash 64-hex SHA-256 of the previous record; use {@code
     *     HashChain.GENESIS_PREV_HASH} for the first record in a stream
     * @param record the record to hash (id, factType, subject, actor, payload, occurredAt,
     *     recordedAt, streamKey, sequenceNo must be set before calling)
     * @return 64-hex SHA-256 string that becomes the record's {@code recordHash}
     */
    String compute(String prevHash, AuditRecord record);

    /**
     * Returns the genesis {@code prevHash} value (all-zeros 64-hex string) used for the very first
     * record in any stream.
     */
    String genesis();
}
