package com.neuroassist.pac.infrastructure.clients;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.neuroassist.pac.application.port.out.EmergencyEligibilityGate.EligibilityResult;
import com.neuroassist.pac.domain.model.Urgencia;
import com.neuroassist.pac.shared.error.CodedException;
import com.neuroassist.pac.shared.error.EligibilityGateUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * INT-PAC-001 / PAC-017 — Unit tests for EmergencyEligibilityGateImpl.
 *
 * <p>Tests the fail-closed vs advisory semantics for emergency vs routine urgency, and validates CB
 * OPEN and network failure propagation.
 */
@ExtendWith(MockitoExtension.class)
class EmergencyEligibilityGateImplTest {

    private static final UUID PRIMITIVO_ID = UUID.randomUUID();

    @Mock private GestDetectabilityClient gestClient;

    private CircuitBreakerRegistry cbRegistry;
    private EmergencyEligibilityGateImpl gate;

    @BeforeEach
    void setUp() {
        // Use a real (open) circuit breaker registry for basic tests
        cbRegistry = CircuitBreakerRegistry.ofDefaults();
        gate = new EmergencyEligibilityGateImpl(gestClient, cbRegistry);
    }

    // =========================================================================
    // Emergency urgency — FAIL-CLOSED
    // =========================================================================

    @Nested
    @DisplayName("Emergency urgency — fail-closed")
    class EmergencyTests {

        @Test
        @DisplayName("GEST returns supported=true → eligible, no warnings")
        void emergency_gestSupported_returnsEligible() {
            when(gestClient.checkDetectability(any()))
                    .thenReturn(new GestDetectabilityResponse(true, "active"));

            EligibilityResult result = gate.check(PRIMITIVO_ID, null, Urgencia.emergencia);

            assertThat(result.eligible()).isTrue();
            assertThat(result.warnings()).isEmpty();
        }

        @Test
        @DisplayName("GEST returns supported=false → CodedException pac.emergency.not-detectable")
        void emergency_gestNotDetectable_throwsCodedException() {
            when(gestClient.checkDetectability(any()))
                    .thenReturn(new GestDetectabilityResponse(false, "pending-training"));

            assertThatThrownBy(() -> gate.check(PRIMITIVO_ID, null, Urgencia.emergencia))
                    .isInstanceOf(CodedException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((CodedException) ex).code())
                                            .isEqualTo("pac.emergency.not-detectable"));
        }

        @Test
        @DisplayName(
                "GEST throws network exception → EligibilityGateUnavailableException (503, Retry-After:30)")
        void emergency_gestNetworkError_throwsGateUnavailable() {
            when(gestClient.checkDetectability(any()))
                    .thenThrow(new RuntimeException("connect timeout"));

            assertThatThrownBy(() -> gate.check(PRIMITIVO_ID, null, Urgencia.emergencia))
                    .isInstanceOf(EligibilityGateUnavailableException.class)
                    .satisfies(
                            ex ->
                                    assertThat(
                                                    ((EligibilityGateUnavailableException) ex)
                                                            .retryAfterSeconds())
                                            .isEqualTo(30));
        }

        @Test
        @DisplayName("CB OPEN → EligibilityGateUnavailableException (503, fail-closed)")
        void emergency_cbOpen_throwsGateUnavailable() {
            // Force CB into OPEN state
            CircuitBreakerConfig forcedOpenConfig =
                    CircuitBreakerConfig.custom()
                            .slidingWindowSize(1)
                            .minimumNumberOfCalls(1)
                            .failureRateThreshold(1.0f) // 1% — any failure trips the CB
                            .build();
            CircuitBreakerRegistry forcedRegistry = CircuitBreakerRegistry.of(forcedOpenConfig);
            CircuitBreaker cb = forcedRegistry.circuitBreaker("gest-service");
            // Record 1 error → 100% > 1% threshold → CB OPEN
            cb.onError(
                    0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("forced"));

            EmergencyEligibilityGateImpl gateWithOpenCb =
                    new EmergencyEligibilityGateImpl(gestClient, forcedRegistry);

            assertThatThrownBy(() -> gateWithOpenCb.check(PRIMITIVO_ID, null, Urgencia.emergencia))
                    .isInstanceOf(EligibilityGateUnavailableException.class)
                    .satisfies(
                            ex ->
                                    assertThat(
                                                    ((EligibilityGateUnavailableException) ex)
                                                            .retryAfterSeconds())
                                            .isEqualTo(30));

            // Client MUST NOT be called when CB is OPEN
            verify(gestClient, never()).checkDetectability(any());
        }
    }

    // =========================================================================
    // Routine urgency — ADVISORY
    // =========================================================================

    @Nested
    @DisplayName("Routine urgency — advisory")
    class RoutineTests {

        @Test
        @DisplayName("GEST returns supported=true → eligible, no warnings")
        void routine_gestSupported_returnsEligible() {
            when(gestClient.checkDetectability(any()))
                    .thenReturn(new GestDetectabilityResponse(true, "active"));

            EligibilityResult result = gate.check(PRIMITIVO_ID, null, Urgencia.rotina);

            assertThat(result.eligible()).isTrue();
            assertThat(result.warnings()).isEmpty();
        }

        @Test
        @DisplayName("GEST unavailable → eligible with warnings[] (advisory, does NOT block)")
        void routine_gestUnavailable_returnsAdvisoryWarning() {
            when(gestClient.checkDetectability(any()))
                    .thenThrow(new RuntimeException("connection refused"));

            EligibilityResult result = gate.check(PRIMITIVO_ID, null, Urgencia.rotina);

            assertThat(result.eligible()).isTrue();
            assertThat(result.warnings()).isNotEmpty();
        }

        @Test
        @DisplayName("GEST returns supported=false → eligible with advisory warning (not blocked)")
        void routine_gestNotDetectable_returnsAdvisoryWarning() {
            when(gestClient.checkDetectability(any()))
                    .thenReturn(new GestDetectabilityResponse(false, "insufficient-data"));

            EligibilityResult result = gate.check(PRIMITIVO_ID, null, Urgencia.rotina);

            assertThat(result.eligible()).isTrue();
            assertThat(result.warnings()).isNotEmpty();
        }
    }
}
