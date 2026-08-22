package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.ActorDirectoryUnavailableException;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port — resolves caregiver → allowed-patient scope from ACS (HIST-012, P4).
 *
 * <p>Called exclusively from read paths with {@code hist:read} scope. NEVER called during ingestion
 * ({@code POST /audit-facts}) per ADR-005 / HIST-012 instruction.
 *
 * <p>Fail-closed contract: implementations MUST throw {@link ActorDirectoryUnavailableException}
 * when ACS is unreachable or the circuit breaker is open — returning an empty list would silently
 * widen the data scope (D-ADR-005 invariant violation).
 */
public interface ActorDirectoryPort {

    /**
     * Returns the list of patient UUIDs that the given caregiver is authorised to read.
     *
     * @param tenantId the caller's tenant (from JWT {@code tenant} claim)
     * @param caregiverId the caregiver's UUID (from JWT {@code sub} claim)
     * @return non-null, non-empty list of allowed patient UUIDs
     * @throws ActorDirectoryUnavailableException if ACS is unreachable or the circuit breaker is
     *     open — never returns empty list on failure
     */
    List<UUID> resolvePatientScope(String tenantId, UUID caregiverId);
}
