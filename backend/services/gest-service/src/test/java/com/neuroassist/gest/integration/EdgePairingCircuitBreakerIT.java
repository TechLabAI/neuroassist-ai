package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.infrastructure.clients.EdgePairingClient;
import com.neuroassist.gest.shared.error.CodedException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifies the Resilience4j circuit breaker on {@link
 * com.neuroassist.gest.infrastructure.clients.EdgePairingAdapter}: opens after consecutive
 * infrastructure failures (5xx) and ignores business errors (404 → device-not-paired).
 *
 * <p>Window reduced to 3 calls via {@code @DynamicPropertySource} for fast execution.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EdgePairingCircuitBreakerIT extends BaseIntegrationTest {

    @MockBean EdgePairingClient edgePairingClient;

    @Autowired EdgePairingPort edgePairingPort;

    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void configureSmallWindow(DynamicPropertyRegistry registry) {
        registry.add(
                "resilience4j.circuitbreaker.instances.edgePairing.sliding-window-size", () -> "3");
        registry.add(
                "resilience4j.circuitbreaker.instances.edgePairing.failure-rate-threshold",
                () -> "100");
        registry.add(
                "resilience4j.circuitbreaker.instances.edgePairing.wait-duration-in-open-state",
                () -> "60s");
    }

    @BeforeEach
    void resetCircuitBreaker() {
        var cb = circuitBreakerRegistry.circuitBreaker("edgePairing");
        // Force reset of state and metrics
        cb.transitionToClosedState();
        cb.reset();
    }

    private static Request dummyRequest() {
        return Request.create(
                Request.HttpMethod.GET,
                "/devices/x/tenant",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
    }

    private static FeignException serviceUnavailable() {
        return FeignException.errorStatus(
                "getTenantForDevice",
                Response.builder()
                        .status(503)
                        .reason("Service Unavailable")
                        .request(dummyRequest())
                        .headers(Map.of())
                        .build());
    }

    private static FeignException.NotFound notFound() {
        return new FeignException.NotFound("Not Found", dummyRequest(), null, Map.of());
    }

    @Test
    void circuitBreaker_opensAfterConsecutiveInfraFailures() {
        when(edgePairingClient.getTenantForDevice(any())).thenThrow(serviceUnavailable());

        // Use a fixed UUID to test CB opening (this test IS expected to leave CB open)
        UUID deviceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        // Fill the 3-call window with infrastructure failures
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
                    .isInstanceOf(CodedException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((CodedException) ex).code())
                                            .isEqualTo("edge-unavailable"));
        }

        // CB is now open — next call hits fallback immediately
        assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("edge-unavailable"));
    }

    @Test
    void circuitBreaker_remainsClosedForBusinessErrors() {
        when(edgePairingClient.getTenantForDevice(any())).thenThrow(notFound());

        // Use a different UUID to avoid CB state interference from the previous test
        UUID deviceId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        // 3 not-found calls — ignored by CB config (business error, not infra failure)
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
                    .isInstanceOf(CodedException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((CodedException) ex).code())
                                            .isEqualTo("device-not-paired"));
        }

        // CB is still closed — same exception type (not the fallback)
        assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("device-not-paired"));
    }
}
