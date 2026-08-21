package com.neuroassist.hist.infrastructure.clients;

import com.neuroassist.hist.application.port.out.ActorDirectoryPort;
import com.neuroassist.hist.domain.model.ActorDirectoryUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link ActorDirectoryPort} via {@link AcsActorDirectoryClient} (Feign) with
 * a Resilience4j circuit breaker (HIST-012, P4).
 *
 * <p>Fail-closed contract (HIST-012 instruction): the fallback method NEVER returns an empty list —
 * it always throws {@link ActorDirectoryUnavailableException}. Returning an empty list would
 * silently widen the data scope to zero records (instead of preventing the request), which is worse
 * than a 503 from the caller's perspective.
 *
 * <p>RULE-06: Feign client and this adapter live in {@code infrastructure.clients}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActorDirectoryAdapter implements ActorDirectoryPort {

    private final AcsActorDirectoryClient acsClient;

    @Override
    @CircuitBreaker(name = "acs-directory", fallbackMethod = "patientScopeFallback")
    public List<UUID> resolvePatientScope(String tenantId, UUID caregiverId) {
        log.debug("Resolving patient scope via ACS: caregiver={} tenant={}", caregiverId, tenantId);
        AcsActorDirectoryClient.PatientScopeResponse scope =
                acsClient.getPatientsByCaregiver(caregiverId);
        return scope == null || scope.pacienteIds() == null ? List.of() : scope.pacienteIds();
    }

    /**
     * Resilience4j CB fallback — always throws, never returns empty list (fail-closed). The
     * signature must match {@link #resolvePatientScope} plus a trailing {@link Throwable} parameter
     * (Resilience4j convention).
     */
    @SuppressWarnings("unused")
    private List<UUID> patientScopeFallback(String tenantId, UUID caregiverId, Throwable ex) {
        log.warn(
                "ACS actor directory unavailable: caregiver={} tenant={} cause={}",
                caregiverId,
                tenantId,
                ex.getMessage());
        throw new ActorDirectoryUnavailableException(
                "ACS actor directory unavailable — cannot resolve patient scope for caregiver "
                        + caregiverId,
                ex);
    }
}
