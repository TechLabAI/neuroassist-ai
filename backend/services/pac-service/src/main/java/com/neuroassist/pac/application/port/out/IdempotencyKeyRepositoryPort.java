package com.neuroassist.pac.application.port.out;

import com.neuroassist.pac.domain.model.IdempotencyKeyRecord;
import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for idempotency key persistence (PAC-023, SPEC-05 §3.3).
 *
 * <p>RULE-01/02: no Spring/JPA imports here — pure interface. Implemented by {@code
 * IdempotencyKeyRepositoryAdapter} in {@code infrastructure.persistence.adapter}.
 */
public interface IdempotencyKeyRepositoryPort {

    /**
     * Looks up an idempotency key entry by composite key.
     *
     * @param compositeKey the full composite key stored in the DB: {@code
     *     {resourceType}:{callerKey}}
     * @return the stored record if present (may be expired — caller checks TTL)
     */
    Optional<IdempotencyKeyRecord> findByKey(String compositeKey);

    /**
     * Persists a new idempotency key entry.
     *
     * @param record the record to store
     * @return the saved record (same content; provided for symmetry)
     */
    IdempotencyKeyRecord save(IdempotencyKeyRecord record);

    /**
     * Lazily purges expired entries older than {@code cutoff}. Called on lookup by {@code
     * IdempotencyService} (lazy purge strategy, SPEC-05 §3.3).
     *
     * @param cutoff delete rows with {@code created_at} strictly before this instant
     */
    void deleteExpiredBefore(Instant cutoff);
}
