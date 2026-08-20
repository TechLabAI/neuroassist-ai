package com.neuroassist.pac.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain record for a single idempotency key entry (SPEC-05 §3.3, PAC-023).
 *
 * <p>Pure POJO — zero Spring/JPA (RULE-01 / RULE-02).
 *
 * <p>Lifecycle: created on the first successful POST that carries an {@code Idempotency-Key}
 * header. A duplicate request arriving within 24 hours with the same {@code key} and matching
 * {@code bodyHash} will receive the original {@code resourceId} back (HTTP 200). A duplicate with a
 * different {@code bodyHash} produces a 409 {@code /errors/idempotency-mismatch}.
 *
 * @param key composite PK: {@code {resourceType}:{callerKey}} — tenant isolation is provided by the
 *     per-schema routing (search_path), so tenantId is NOT embedded in the key stored in the DB
 *     column.
 * @param resourceId UUID of the created resource (Paciente.id or Comando.id).
 * @param bodyHash SHA-256 hex of the canonical request body string.
 * @param createdAt creation timestamp — used to enforce the 24-hour dedup window.
 */
public record IdempotencyKeyRecord(
        String key, UUID resourceId, String bodyHash, Instant createdAt) {}
