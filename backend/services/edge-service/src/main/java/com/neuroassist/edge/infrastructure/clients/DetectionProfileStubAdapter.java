package com.neuroassist.edge.infrastructure.clients;

import com.neuroassist.edge.application.port.out.DetectionProfilePort;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link DetectionProfilePort} — simulates GEST being unreachable.
 *
 * <p>Non-primary: {@link DetectionProfileFeignAdapter} is the real implementation. Kept for unit
 * tests and dev profiles with no GEST connection.
 *
 * <p>Returns {@link DetectionProfilePort.ProfilePointer#unknown()} rather than "no active profile":
 * a stub that cannot reach GEST is the <i>unknown</i> case, and saying so keeps the two states
 * distinct even here.
 */
@Slf4j
@Component
public class DetectionProfileStubAdapter implements DetectionProfilePort {

    @Override
    public ProfilePointer currentProfileVersion(UUID deviceId, String tenantId) {
        log.debug("DetectionProfileStubAdapter: degraded pointer for deviceId='{}'", deviceId);
        return ProfilePointer.unknown();
    }
}
