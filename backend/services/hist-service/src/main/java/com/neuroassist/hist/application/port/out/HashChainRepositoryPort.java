package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.HashChain;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port — persistence of hash-chain records for stream integrity (P3, HIST-010). Each entry
 * records the position and hash for one AuditRecord within a stream. P1 stub: no implementation
 * yet.
 */
public interface HashChainRepositoryPort {

    /** Persists a hash-chain record entry for a given (streamKey, sequenceNo). */
    HashChain save(HashChain hashChain, String tenantId);

    /**
     * Finds all hash-chain records for a stream ordered by sequenceNo ASC. Used during chain
     * verification (P2).
     */
    List<HashChain> findByStreamKey(String streamKey, String tenantId);

    /** Finds a specific hash-chain record by stream key and sequence number. */
    Optional<HashChain> findByStreamKeyAndSequenceNo(
            String streamKey, long sequenceNo, String tenantId);
}
