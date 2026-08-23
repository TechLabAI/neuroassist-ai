package com.neuroassist.edge.infrastructure.clients;

import com.neuroassist.edge.application.port.out.DetectionProfilePort;
import com.neuroassist.edge.infrastructure.security.ServiceTokenUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Feign-based implementation of {@link DetectionProfilePort} (EDGE-014).
 *
 * <p><b>404 is an answer, not a failure.</b> GEST returns 404 when the tenant has published no
 * profile yet, and Feign surfaces that as an exception. Left to reach the circuit breaker it would
 * count as a failure, so a perfectly healthy tenant that has published nothing would degrade the
 * pointer and, after enough heartbeats, open the breaker against a GEST that is working fine. It is
 * caught here and translated into {@link DetectionProfilePort.ProfilePointer#noActiveProfile()},
 * which returns normally and is recorded as a success. The same exception is also listed under
 * {@code ignoreExceptions} in application.yml — belt and braces, and the config is where a reader
 * looks to find out what the breaker measures.
 *
 * <p><b>What still degrades.</b> Timeouts, connection failures and 5xx: cases where the pointer is
 * genuinely unknown. The heartbeat is accepted either way — liveness never depends on GEST — but
 * the response now says which of the two happened.
 *
 * <p><b>What is never degraded.</b> A missing service identity is a configuration defect, not a
 * transient outage; swallowing it is what produced C-19, so it is rethrown.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class DetectionProfileFeignAdapter implements DetectionProfilePort {

    private final DetectionProfileClient detectionProfileClient;

    @Override
    @CircuitBreaker(name = "gest-detection-profile", fallbackMethod = "fallback")
    public ProfilePointer currentProfileVersion(UUID deviceId, String tenantId) {
        ResponseEntity<GestDetectionProfileView> response;
        try {
            response = detectionProfileClient.getActiveProfileForDevice(deviceId);
        } catch (FeignException.NotFound e) {
            log.debug("GEST has no active profile for deviceId='{}'", deviceId);
            return ProfilePointer.noActiveProfile();
        }

        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            // Reachable when a decoder maps 404 to a body instead of throwing.
            return ProfilePointer.noActiveProfile();
        }
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            long version = response.getBody().version();
            log.debug("GEST profile version={} for deviceId='{}'", version, deviceId);
            return ProfilePointer.of(version);
        }

        log.warn(
                "GEST: unexpected status {} for deviceId='{}'", response.getStatusCode(), deviceId);
        return ProfilePointer.unknown();
    }

    @SuppressWarnings("unused")
    ProfilePointer fallback(UUID deviceId, String tenantId, Throwable cause) {
        if (cause instanceof ServiceTokenUnavailableException stue) {
            log.error(
                    "Service identity unavailable for GEST call (deviceId='{}'): {}",
                    deviceId,
                    stue.getMessage());
            throw stue;
        }
        log.warn(
                "GEST unreachable — profile pointer degraded for deviceId='{}': {}",
                deviceId,
                cause.getMessage());
        return ProfilePointer.unknown();
    }
}
