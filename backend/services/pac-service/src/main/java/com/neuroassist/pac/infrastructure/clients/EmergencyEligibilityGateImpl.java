package com.neuroassist.pac.infrastructure.clients;

import com.neuroassist.pac.application.port.out.EmergencyEligibilityGate;
import com.neuroassist.pac.application.port.out.EmergencyEligibilityGate.EligibilityResult;
import com.neuroassist.pac.domain.model.Urgencia;
import com.neuroassist.pac.shared.error.CodedException;
import com.neuroassist.pac.shared.error.EligibilityGateUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PAC-017 — Infrastructure adapter implementing {@link EmergencyEligibilityGate}.
 *
 * <p>Calls {@link GestDetectabilityClient} via the Resilience4j circuit breaker named {@code
 * gest-service}. Fail-closed semantics for emergency urgency (ADR-005):
 *
 * <ul>
 *   <li>CB OPEN → {@link EligibilityGateUnavailableException} (503 + Retry-After:30)
 *   <li>Network / timeout → {@link EligibilityGateUnavailableException} (503 + Retry-After:30)
 *   <li>GEST supported=false → {@link CodedException} pac.emergency.not-detectable (422)
 * </ul>
 *
 * <p>For {@code urgencia=rotina}, any GEST failure returns an advisory warning and allows publish.
 *
 * <p>RULE-06: @FeignClient ({@link GestDetectabilityClient}) used only within
 * infrastructure.clients. RULE-05: no api.* imports.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmergencyEligibilityGateImpl implements EmergencyEligibilityGate {

    private static final String CB_NAME = "gest-service";
    private static final int RETRY_AFTER_SECONDS = 30;

    private final GestDetectabilityClient gestClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public EligibilityResult check(
            UUID primitivoPrincipalId, UUID primitivoSecundarioId, Urgencia urgencia) {

        GestDetectabilityRequest request =
                new GestDetectabilityRequest(primitivoPrincipalId, primitivoSecundarioId);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(CB_NAME);

        try {
            GestDetectabilityResponse response =
                    cb.executeSupplier(() -> gestClient.checkDetectability(request));

            return evaluateResponse(response, urgencia);

        } catch (EligibilityGateUnavailableException e) {
            // Re-throw — already wrapped with correct semantics
            throw e;
        } catch (CodedException e) {
            // Re-throw domain exceptions (e.g. emergency-not-detectable)
            throw e;
        } catch (FeignException.NotFound e) {
            // GEST returned 404: primitive is unknown to GEST — reject regardless of urgency
            log.warn(
                    "GEST returned 404 for primitive={} — primitive unknown to GEST",
                    primitivoPrincipalId);
            throw new CodedException(
                    "pac.primitive.unknown-to-gest",
                    "Primitive '" + primitivoPrincipalId + "' is not registered in GEST");
        } catch (CallNotPermittedException e) {
            // Circuit breaker is OPEN — fail-closed for emergency, advisory for routine
            log.warn(
                    "Emergency eligibility gate: CB '{}' is OPEN for primitive={} urgencia={}",
                    CB_NAME,
                    primitivoPrincipalId,
                    urgencia);
            return handleGestUnavailable(urgencia, "Circuit breaker is OPEN for gest-service", e);
        } catch (Exception e) {
            // Network error, timeout, or unexpected failure from GEST
            log.warn(
                    "Emergency eligibility gate: GEST unreachable for primitive={} urgencia={} cause={}",
                    primitivoPrincipalId,
                    urgencia,
                    e.getMessage());
            return handleGestUnavailable(
                    urgencia, "GEST service unreachable: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Evaluates the GEST response and applies fail-closed / advisory logic.
     *
     * <p>If {@code supported=false}:
     *
     * <ul>
     *   <li>Emergency → throw 422 pac.emergency.not-detectable
     *   <li>Routine → return advisory result (eligible=true with warning)
     * </ul>
     */
    private EligibilityResult evaluateResponse(
            GestDetectabilityResponse response, Urgencia urgencia) {
        if (response.supported()) {
            log.debug("GEST detectability check passed: status={}", response.status());
            return EligibilityResult.ok();
        }

        // GEST reported not-supported
        if (urgencia == Urgencia.emergencia) {
            log.warn("Emergency command not detectable by GEST — status={}", response.status());
            throw new CodedException(
                    "pac.emergency.not-detectable",
                    "Emergency command cannot be published: GEST reports gesture as '"
                            + response.status()
                            + "'. The patient's safety depends on gesture detectability.");
        }

        // Routine: advisory — not detectable but we allow publish with a warning
        log.info(
                "GEST reports routine command as not-detectable (advisory): status={}",
                response.status());
        return EligibilityResult.withWarnings(List.of("gest-not-detectable: " + response.status()));
    }

    /**
     * Handles GEST unavailability based on urgency.
     *
     * <p>Emergency: FAIL-CLOSED — always throws {@link EligibilityGateUnavailableException}.
     * Routine: ADVISORY — returns eligible with a warning.
     */
    private EligibilityResult handleGestUnavailable(
            Urgencia urgencia, String reason, Exception cause) {
        if (urgencia == Urgencia.emergencia) {
            throw new EligibilityGateUnavailableException(
                    RETRY_AFTER_SECONDS,
                    "Emergency eligibility gate unavailable — cannot confirm gesture detectability for"
                            + " emergency command. Reason: "
                            + reason,
                    cause);
        }

        // Routine: advisory — return eligible with warning, publish proceeds
        log.info("GEST unavailable for routine command (advisory). Reason: {}", reason);
        return EligibilityResult.withWarnings(List.of("gest-service-unavailable"));
    }
}
