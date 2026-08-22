package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.StreamHead;
import java.util.Optional;

/**
 * Outbound port — mutable stream-head cache persistence (P3, HIST-010). Provides the low-level
 * persistence layer consumed by {@link StreamHeadPort}. P1 stub: no implementation yet.
 */
public interface StreamHeadRepositoryPort {

    /**
     * Inserts or updates the stream head for (streamKey, tenantId). Locks the row with
     * PESSIMISTIC_WRITE to serialise concurrent appends.
     */
    StreamHead save(StreamHead head, String tenantId);

    /**
     * Fetches the current stream head with a pessimistic write lock. Returns empty if the stream
     * has no records yet (genesis append).
     */
    Optional<StreamHead> findByStreamKeyForUpdate(String streamKey, String tenantId);
}
