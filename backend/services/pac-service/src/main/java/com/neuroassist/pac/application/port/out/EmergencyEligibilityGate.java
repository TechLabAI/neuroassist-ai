package com.neuroassist.pac.application.port.out;

import com.neuroassist.pac.domain.model.Urgencia;
import java.util.List;
import java.util.UUID;

/**
 * PAC-017 — Outbound port for the Emergency Eligibility Gate (ADR-005, SPEC-03).
 *
 * <p>Semantics:
 *
 * <ul>
 *   <li>For {@code urgencia=emergencia}: gate is FAIL-CLOSED. If GEST is unreachable, times out, or
 *       the circuit breaker is OPEN, the implementation MUST throw {@link
 *       com.neuroassist.pac.shared.error.EligibilityGateUnavailableException} (HTTP 503 +
 *       Retry-After:30). If GEST returns {@code supported=false} it MUST throw a {@link
 *       com.neuroassist.pac.shared.error.CodedException} with code {@code
 *       pac.emergency.not-detectable} (HTTP 422).
 *   <li>For {@code urgencia=rotina}: gate is ADVISORY. GEST unavailability is logged and returned
 *       as a warning in {@link EligibilityResult#warnings()}, and publish proceeds normally.
 * </ul>
 *
 * <p>RULE-06: @FeignClient is in infrastructure.clients; this interface lives in
 * application.port.out and has no Feign/Spring imports.
 */
public interface EmergencyEligibilityGate {

    /**
     * Checks gesture detectability for a command and returns an eligibility result.
     *
     * @param primitivoPrincipalId UUID of the principal gesture primitive (required)
     * @param primitivoSecundarioId UUID of the secondary primitive (null for single-gesture
     *     commands)
     * @param urgencia urgency classification of the command
     * @return {@link EligibilityResult} — always returns for routine; throws for emergency failures
     * @throws com.neuroassist.pac.shared.error.EligibilityGateUnavailableException if urgencia is
     *     emergencia and GEST is unreachable or CB is OPEN
     * @throws com.neuroassist.pac.shared.error.CodedException with code {@code
     *     pac.emergency.not-detectable} if urgencia is emergencia and GEST returns supported=false
     */
    EligibilityResult check(
            UUID primitivoPrincipalId, UUID primitivoSecundarioId, Urgencia urgencia);

    /**
     * Verifies that every referenced primitive is known to GEST. Used by the {@code :validate}
     * transition, which must reject unknown primitive references with HTTP 422 (SPEC-02 EARS).
     *
     * <p>The eligibility check above evaluates the tenant's primitive set as a whole and therefore
     * cannot answer this question — the implementation resolves each primitive individually.
     *
     * <p>GEST unavailability is advisory here: if the lookup cannot be performed the primitives are
     * treated as known and validation proceeds.
     *
     * @param primitivoPrincipalId UUID of the principal gesture primitive (required)
     * @param primitivoSecundarioId UUID of the secondary primitive (null for single-gesture
     *     commands)
     * @throws com.neuroassist.pac.shared.error.CodedException with code {@code
     *     pac.primitive.unknown-to-gest} if GEST answers 404 for a referenced primitive
     */
    void verifyPrimitivesKnown(UUID primitivoPrincipalId, UUID primitivoSecundarioId);

    /**
     * Result of an eligibility check.
     *
     * @param eligible whether the command is eligible to publish (always true for emergencia if no
     *     exception was thrown; may be false for rotina if GEST reports not-supported)
     * @param warnings human-readable advisory messages for the caller to surface in the response;
     *     empty for emergency (gate never returns advisory — it either succeeds or throws)
     */
    record EligibilityResult(boolean eligible, List<String> warnings) {

        /** Convenience factory for a clean (no warnings) eligible result. */
        public static EligibilityResult ok() {
            return new EligibilityResult(true, List.of());
        }

        /** Convenience factory for a routine-advisory result (eligible but with warnings). */
        public static EligibilityResult withWarnings(List<String> warnings) {
            return new EligibilityResult(true, warnings);
        }
    }
}
