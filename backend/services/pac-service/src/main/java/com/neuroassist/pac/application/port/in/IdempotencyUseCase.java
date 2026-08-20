package com.neuroassist.pac.application.port.in;

import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port for idempotency key check and registration (PAC-023, SPEC-05 §3).
 *
 * <p>RULE-01/02: no Spring/JPA imports. Implemented by {@link
 * com.neuroassist.pac.application.service.IdempotencyService}.
 *
 * <p>Usage pattern in controllers:
 *
 * <pre>{@code
 * if (idempotencyKey != null) {
 *     Optional<UUID> existing = idempotencyUseCase.checkKey(idempotencyKey, "paciente", bodyHash);
 *     if (existing.isPresent()) {
 *         // replay: return 200 with original resource
 *     }
 * }
 * // proceed with creation
 * UUID newId = ...;
 * if (idempotencyKey != null) {
 *     idempotencyUseCase.registerKey(idempotencyKey, "paciente", newId, bodyHash);
 * }
 * }</pre>
 */
public interface IdempotencyUseCase {

    /**
     * Checks whether an unexpired idempotency key entry exists for the current tenant.
     *
     * <ul>
     *   <li>Same key + matching body hash → returns {@code Optional.of(existingResourceId)}.
     *   <li>Same key + different body hash → throws {@link
     *       com.neuroassist.pac.shared.error.CodedException} with code {@code
     *       pac.idempotency-mismatch} (SPEC-05 §3.2 → HTTP 409).
     *   <li>Key not found or expired → returns {@code Optional.empty()} (proceed with creation).
     * </ul>
     *
     * @param idempotencyKey the raw {@code Idempotency-Key} header value (UUID v4)
     * @param resourceType resource-type discriminator: {@code "paciente"} or {@code "comando"}
     * @param bodyHash SHA-256 hex of the canonical request body
     * @return existing resource id if this is a duplicate, empty if new
     */
    Optional<UUID> checkKey(String idempotencyKey, String resourceType, String bodyHash);

    /**
     * Registers a newly created resource against an idempotency key.
     *
     * <p>Must be called after successful resource creation when the {@code Idempotency-Key} header
     * was present. Idempotent on a concurrent race: if the key was already registered (extremely
     * rare simultaneous duplicate), silently skips the insert.
     *
     * @param idempotencyKey the raw {@code Idempotency-Key} header value
     * @param resourceType same discriminator used in {@link #checkKey}
     * @param resourceId UUID of the newly created resource
     * @param bodyHash SHA-256 hex of the canonical request body
     */
    void registerKey(String idempotencyKey, String resourceType, UUID resourceId, String bodyHash);
}
