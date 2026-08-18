package com.neuroassist.edge.infrastructure.clients;

import com.neuroassist.edge.application.port.out.DetectionProfilePort;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link DetectionProfilePort}. Returns {@link Optional#empty()} — simulates
 * GEST unavailable (degraded mode). Kept as a non-primary bean for use in unit tests and dev
 * profiles where no real GEST connection is desired.
 *
 * <p>{@code @Primary} was removed in P4 (EDGE-014): {@link DetectionProfileFeignAdapter} is now the
 * primary implementation.
 */
@Slf4j
@Component
public class DetectionProfileStubAdapter implements DetectionProfilePort {

    @Override
    public Optional<Long> currentProfileVersion(UUID deviceId, String tenantId) {
        log.debug(
                "DetectionProfileStubAdapter: returning empty (P2 stub) for deviceId='{}'",
                deviceId);
        return Optional.empty();
    }
}
