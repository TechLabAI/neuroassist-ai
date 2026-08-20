package com.neuroassist.gest.infrastructure.clients;

import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.infrastructure.clients.TenantPairingResponse;
import com.neuroassist.gest.shared.error.CodedException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter for {@link EdgePairingPort} using Feign to call edge-service.
 *
 * <p>Error mapping (RULE-05: infrastructure.* must not depend on api.*):
 *
 * <ul>
 *   <li>HTTP 404 from edge-service → {@code CodedException("device-not-paired")} (403)
 *   <li>device found but {@code paired=false} → {@code CodedException("device-not-paired")} (403)
 *   <li>Any other {@link FeignException} → {@code CodedException("edge-unavailable")} (503)
 * </ul>
 *
 * <p>The {@code @Primary} stub ({@link
 * com.neuroassist.gest.infrastructure.persistence.adapter.EdgePairingPortStub}) overrides this bean
 * in the {@code poc} profile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EdgePairingAdapter implements EdgePairingPort {

    private final EdgePairingClient edgePairingClient;

    @CircuitBreaker(name = "edgePairing", fallbackMethod = "edgePairingFallback")
    @Override
    public String checkPairingAndGetTenant(UUID deviceId) {
        try {
            TenantPairingResponse response = edgePairingClient.checkPairingAndGetTenant(deviceId);
            if (!response.paired()) {
                log.warn("Device {} exists in edge-service but is not paired", deviceId);
                throw new CodedException("device-not-paired", "Device not paired: " + deviceId)
                        .withContext(Map.of("deviceId", deviceId.toString()));
            }
            log.debug("Device {} paired with tenant {}", deviceId, response.tenantId());
            return response.tenantId();
        } catch (FeignException.NotFound e) {
            log.warn("Device {} not found in edge-service", deviceId);
            throw new CodedException("device-not-paired", "Device not found: " + deviceId)
                    .withContext(Map.of("deviceId", deviceId.toString()));
        } catch (CodedException e) {
            // Re-throw CodedException without wrapping (handles the not-paired case above)
            throw e;
        } catch (FeignException e) {
            log.error(
                    "Edge service unavailable while checking pairing for deviceId={}", deviceId, e);
            throw new CodedException(
                    "edge-unavailable", "Edge service unavailable: " + e.getMessage(), e);
        }
    }

    private String edgePairingFallback(UUID deviceId, Throwable cause) {
        log.warn(
                "EdgePairing circuit breaker open for device {}: {}", deviceId, cause.getMessage());
        // If the cause is a 404 or device-not-paired error, propagate that instead of
        // edge-unavailable
        if (cause instanceof CodedException ce && "device-not-paired".equals(ce.code())) {
            throw ce;
        }
        if (cause instanceof FeignException.NotFound) {
            throw new CodedException("device-not-paired", "Device not found: " + deviceId)
                    .withContext(Map.of("deviceId", deviceId.toString()));
        }
        throw new CodedException(
                "edge-unavailable",
                "edge-unavailable: Edge pairing circuit breaker open: " + cause.getMessage(),
                cause);
    }
}
