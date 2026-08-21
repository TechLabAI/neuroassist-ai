package com.neuroassist.edge.infrastructure.clients;

import com.neuroassist.edge.application.port.out.DetectionProfilePort;
import com.neuroassist.edge.infrastructure.security.ServiceTokenUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Real Feign-based implementation of {@link DetectionProfilePort} (EDGE-014, P4). Replaces the P2
 * stub ({@link DetectionProfileStubAdapter}) as the {@code @Primary} bean.
 *
 * <p>Resilience4j circuit breaker {@code gest-detection-profile}: when GEST is unavailable (Feign
 * exception or timeout), {@code fallback()} returns {@link Optional#empty()} — heartbeat continues
 * in degraded mode ({@code profilePointerDegraded=true}). Circuit breaker thresholds are configured
 * high in {@code application-test.yml} to prevent WireMock interference.
 *
 * <p>RULE-06: @FeignClient only in infrastructure.clients (caller of the port). This class lives in
 * the same package. RULE-09: base package com.neuroassist.edge.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class DetectionProfileFeignAdapter implements DetectionProfilePort {

    private final DetectionProfileClient detectionProfileClient;

    @Override
    @CircuitBreaker(name = "gest-detection-profile", fallbackMethod = "fallback")
    public Optional<Long> currentProfileVersion(UUID deviceId, String tenantId) {
        ResponseEntity<GestDetectionProfileView> response =
                detectionProfileClient.getActiveProfileForDevice(deviceId);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            long version = response.getBody().version();
            log.debug("GEST profile version={} for deviceId='{}'", version, deviceId);
            return Optional.of(version);
        }

        log.warn(
                "GEST: unexpected status {} for deviceId='{}'", response.getStatusCode(), deviceId);
        return Optional.empty();
    }

    /**
     * Resilience4j fallback — invoked when circuit is open or Feign throws any exception. Returns
     * degraded empty so the heartbeat is still accepted with {@code profilePointerDegraded=true}.
     */
    @SuppressWarnings("unused")
    Optional<Long> fallback(UUID deviceId, String tenantId, Throwable cause) {
        // Fail-closed: a missing service identity is a configuration/authentication defect, not a
        // transient GEST outage. Degrading it to Optional.empty() is exactly the silent failure
        // that produced C-19, so it is rethrown and surfaces to the caller.
        if (cause instanceof ServiceTokenUnavailableException stue) {
            log.error(
                    "Service identity unavailable for GEST call (deviceId='{}'): {}",
                    deviceId,
                    stue.getMessage());
            throw stue;
        }
        log.warn(
                "GEST circuit breaker fallback (degraded) for deviceId='{}': {}",
                deviceId,
                cause.getMessage());
        return Optional.empty();
    }
}
