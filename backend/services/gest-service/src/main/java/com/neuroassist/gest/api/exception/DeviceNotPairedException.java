package com.neuroassist.gest.api.exception;

import com.neuroassist.gest.shared.error.CodedException;
import java.util.Map;
import java.util.UUID;

/**
 * Thrown when a device is not registered or not paired in the edge service.
 *
 * <p>Maps to HTTP 403 via {@link GlobalExceptionHandler} (error code: {@code device-not-paired}).
 * For use in application-layer services; the infrastructure adapter throws {@link CodedException}
 * directly to comply with RULE-05.
 */
public class DeviceNotPairedException extends CodedException {

    public DeviceNotPairedException(UUID deviceId) {
        super("device-not-paired", "Device not paired: " + deviceId);
        withContext(Map.of("deviceId", deviceId.toString()));
    }
}
