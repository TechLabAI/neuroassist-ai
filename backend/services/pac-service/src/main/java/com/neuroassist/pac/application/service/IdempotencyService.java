package com.neuroassist.pac.application.service;

import com.neuroassist.pac.application.port.in.IdempotencyUseCase;
import com.neuroassist.pac.application.port.out.IdempotencyKeyRepositoryPort;
import com.neuroassist.pac.domain.model.IdempotencyKeyRecord;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import com.neuroassist.pac.shared.error.CodedException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAC-023 — Idempotency-Key deduplication use case (SPEC-05 §3).
 *
 * <p>Hexagonal rules:
 *
 * <ul>
 *   <li>RULE-03: {@code @Transactional} only here — never in controller or adapter.
 *   <li>RULE-07: {@link TenantContextHolder#require()} on every method that builds the
 *       tenant-scoped composite key.
 *   <li>RULE-01/02: domain record {@link IdempotencyKeyRecord} has no Spring/JPA imports.
 * </ul>
 *
 * <p>Deduplication window: 24 hours from first request (SPEC-05 §3.2). Composite key stored in DB:
 * {@code {resourceType}:{callerKey}}; tenant isolation is provided by per-schema routing
 * (search_path).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService implements IdempotencyUseCase {

    /** 24-hour deduplication window (SPEC-05 §3.2). */
    static final long WINDOW_SECONDS = 24 * 3600L;

    private final IdempotencyKeyRepositoryPort idempotencyKeyRepository;

    // -------------------------------------------------------------------------
    // IdempotencyUseCase.checkKey
    // -------------------------------------------------------------------------

    /**
     * Checks whether the given idempotency key has been seen within the 24h window.
     *
     * <ul>
     *   <li>Same key + matching hash → returns {@code Optional.of(existingResourceId)}.
     *   <li>Same key + different hash → throws 409 {@code pac.idempotency-mismatch}.
     *   <li>Not found or expired → returns {@code Optional.empty()}.
     * </ul>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> checkKey(String idempotencyKey, String resourceType, String bodyHash) {
        String tenantId = TenantContextHolder.require(); // RULE-07
        String compositeKey = compositeKey(resourceType, idempotencyKey);

        return idempotencyKeyRepository
                .findByKey(compositeKey)
                .filter(r -> isUnexpired(r.createdAt()))
                .map(
                        r -> {
                            if (!r.bodyHash().equals(bodyHash)) {
                                log.warn(
                                        "Idempotency-Key mismatch tenantId={} resourceType={} key={}",
                                        tenantId,
                                        resourceType,
                                        idempotencyKey);
                                throw new CodedException(
                                                "pac.idempotency-mismatch",
                                                "Idempotency-Key already used with a different request body")
                                        .withContext(
                                                Map.of(
                                                        "idempotencyKey", idempotencyKey,
                                                        "resourceType", resourceType));
                            }
                            log.debug(
                                    "Idempotency-Key hit tenantId={} resourceType={} key={} resourceId={}",
                                    tenantId,
                                    resourceType,
                                    idempotencyKey,
                                    r.resourceId());
                            return r.resourceId();
                        });
    }

    // -------------------------------------------------------------------------
    // IdempotencyUseCase.registerKey
    // -------------------------------------------------------------------------

    /**
     * Registers a newly created resource against the caller's idempotency key.
     *
     * <p>If the key is already registered (extremely rare simultaneous race), silently skips the
     * insert to avoid a unique-constraint exception at the DB level.
     */
    @Override
    @Transactional
    public void registerKey(
            String idempotencyKey, String resourceType, UUID resourceId, String bodyHash) {
        String tenantId = TenantContextHolder.require(); // RULE-07
        String compositeKey = compositeKey(resourceType, idempotencyKey);

        // Best-effort guard against concurrent duplicates — DB unique constraint is authoritative.
        if (idempotencyKeyRepository.findByKey(compositeKey).isEmpty()) {
            IdempotencyKeyRecord record =
                    new IdempotencyKeyRecord(compositeKey, resourceId, bodyHash, Instant.now());
            idempotencyKeyRepository.save(record);
            log.info(
                    "Idempotency-Key registered tenantId={} resourceType={} key={} resourceId={}",
                    tenantId,
                    resourceType,
                    idempotencyKey,
                    resourceId);
        } else {
            log.debug("Idempotency-Key already registered (concurrent race) key={}", compositeKey);
        }
    }

    // -------------------------------------------------------------------------
    // Package-visible helpers (for unit tests)
    // -------------------------------------------------------------------------

    /** Builds the composite key stored in the DB column (no tenantId — schema scopes it). */
    static String compositeKey(String resourceType, String idempotencyKey) {
        return resourceType + ":" + idempotencyKey;
    }

    private boolean isUnexpired(Instant createdAt) {
        return createdAt.isAfter(Instant.now().minusSeconds(WINDOW_SECONDS));
    }
}
