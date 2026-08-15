package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.infrastructure.clients.EdgePairingClient;
import com.neuroassist.gest.shared.error.CodedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Integration test for the Resilience4j CircuitBreaker on {@link
 * com.neuroassist.gest.infrastructure.clients.EdgePairingAdapter}.
 *
 * <p>Uses the Testcontainers singleton container pattern (static initializer in {@link
 * BaseIntegrationTest} — NOT @Testcontainers + @Container). The circuit breaker state is reset
 * before each test to ensure test isolation.
 *
 * <p>Covers GEST-025 acceptance criteria (P9):
 *
 * <ul>
 *   <li>5 consecutive failures open the circuit (minimumNumberOfCalls=5, threshold=50%)
 *   <li>Fallback response with open circuit arrives in less than 100 ms
 *   <li>No biometric field is referenced in any assertion or log
 * </ul>
 */
class CircuitBreakerIT extends BaseIntegrationTest {

    /** Mock replaces the Feign-generated proxy so no real HTTP calls are made. */
    @MockBean EdgePairingClient edgePairingClient;

    /**
     * Resolves to the {@link com.neuroassist.gest.infrastructure.clients.EdgePairingAdapter} AOP
     * proxy (profile "!poc" is active in test profile).
     */
    @Autowired EdgePairingPort edgePairingPort;

    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreakerState() {
        circuitBreakerRegistry.circuitBreaker("edgePairing").reset();
    }

    /**
     * Verifies that 5 consecutive failures (filling the minimumNumberOfCalls window at 100% failure
     * rate &gt; 50% threshold) transition the circuit to OPEN.
     */
    @Test
    void fiveConsecutiveFailures_openCircuit() {
        when(edgePairingClient.checkPairingAndGetTenant(any()))
                .thenThrow(new RuntimeException("EDGE simulated failure — not a biometric field"));

        UUID deviceId = UUID.randomUUID();

        // Call 5 times to fill minimumNumberOfCalls window; all fail → 100% > 50% threshold
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
                    .isInstanceOf(CodedException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((CodedException) ex).code())
                                            .isEqualTo("edge-unavailable"));
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("edgePairing");
        assertThat(cb.getState())
                .as("Circuit must be OPEN after 5 consecutive failures")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    /**
     * Verifies that when the circuit is already OPEN, the fallback is returned immediately (well
     * under 100 ms) without waiting for any remote timeout.
     */
    @Test
    void openCircuit_fallbackReturnedInUnder100ms() {
        // Force circuit to OPEN state directly — no need to fill the window
        circuitBreakerRegistry.circuitBreaker("edgePairing").transitionToOpenState();

        UUID deviceId = UUID.randomUUID();

        long startNs = System.nanoTime();
        assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("edge-unavailable"));
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        assertThat(elapsedMs)
                .as(
                        "Fallback with open circuit must complete in under 100 ms; was %d ms",
                        elapsedMs)
                .isLessThan(100L);
    }
}
