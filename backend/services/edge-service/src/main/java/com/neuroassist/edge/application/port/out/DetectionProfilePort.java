package com.neuroassist.edge.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — detection profile version pointer (GEST). Stub implementation (Optional.empty())
 * used in P2. Feign-based real implementation arrives in P4 (EDGE-014).
 */
public interface DetectionProfilePort {

    /**
     * Retrieve the current active detection-profile version for the given device/tenant. Returns
     * {@link Optional#empty()} when GEST is unavailable (degraded mode).
     *
     * @param deviceId the device UUID
     * @param tenantId the tenant identifier
     * @return the current profile version, or empty if GEST is unavailable
     */
    Optional<Long> currentProfileVersion(UUID deviceId, String tenantId);
}
