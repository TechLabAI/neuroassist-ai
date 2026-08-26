package com.neuroassist.gest.application.port.out;

import java.util.UUID;

/**
 * Outbound port — verifies device pairing with edge-service and returns the associated tenantId.
 *
 * <p>Implementations must throw a {@link com.neuroassist.gest.shared.error.CodedException} with
 * code {@code "device-not-paired"} (HTTP 403) when the device is not paired, and code {@code
 * "edge-unavailable"} (HTTP 503) when the edge service is unreachable.
 *
 * <p>RULE-06: only {@code infrastructure.clients} implements this port via Feign.
 */
public interface EdgePairingPort {

    /**
     * Checks that deviceId is paired in the edge service and returns its tenantId.
     *
     * @param deviceId the device UUID to check
     * @return tenantId associated with the paired device
     */
    String checkPairingAndGetTenant(UUID deviceId);
}
