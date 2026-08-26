package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.StreamHead;
import java.util.Optional;

/**
 * Outbound port — manage the mutable stream-tip cache for hash-chain append (ADR-006).
 * Implementations MUST acquire a pessimistic write lock before returning the tip so that concurrent
 * appends to the same stream are serialised.
 */
public interface StreamHeadPort {

    /**
     * Fetches the current tip of a stream while holding a pessimistic write lock. Returns empty if
     * this is the first append to the stream (genesis record).
     *
     * @param streamKey canonical stream key, e.g. {@code "paciente:<uuid>"}
     * @param tenantId tenant schema identifier
     * @return the current stream head under lock, or empty for a new stream
     */
    Optional<StreamHead> lockAndFetchTip(String streamKey, String tenantId);

    /**
     * Inserts or updates the stream head after a successful append. Must be called within the same
     * transaction as the ledger insert.
     *
     * @param head the new stream head (with updated sequenceNo and currentHash)
     * @param tenantId tenant schema identifier (for schema routing)
     * @return the persisted stream head
     */
    StreamHead upsert(StreamHead head, String tenantId);
}
