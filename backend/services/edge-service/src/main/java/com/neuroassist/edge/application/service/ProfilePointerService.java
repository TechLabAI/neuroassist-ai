package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.ProfilePointerUseCase;
import com.neuroassist.edge.application.port.out.DetectionProfilePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ProfilePointerService — collaborator of {@link HeartbeatService} and {@link LivenessService}.
 * Fetches the active profile version pointer from GEST and computes drift (c4-component.puml:24/:58
 * — "fetches active profile version pointer from GEST; computes drift").
 *
 * <p>Not {@code @Transactional}: this service makes no DB access. It delegates to {@link
 * DetectionProfilePort} (Feign client backed by GEST) and applies the three-state semantics of
 * {@code DetectionProfilePort.ProfilePointer}.
 *
 * <p>Exception handling: any exception from the Feign call is caught here and mapped to {@code
 * ProfilePointer.unknown()} so that GEST unavailability never propagates to callers as a runtime
 * exception (heartbeat / liveness are independent of GEST health).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfilePointerService implements ProfilePointerUseCase {

    private final DetectionProfilePort detectionProfilePort;

    @Override
    public ProfileDriftResult resolveProfileDrift(
            UUID deviceId, String tenantId, Long runningProfileVersion) {
        DetectionProfilePort.ProfilePointer pointer;
        try {
            pointer = detectionProfilePort.currentProfileVersion(deviceId, tenantId);
        } catch (Exception e) {
            log.warn(
                    "GEST unavailable resolving profile pointer deviceId='{}': {}",
                    deviceId,
                    e.getMessage());
            pointer = DetectionProfilePort.ProfilePointer.unknown();
        }

        boolean degraded = pointer.degraded();
        Long activeProfileVersion = pointer.version();

        // Three-state drift semantics:
        //   degraded (GEST unreachable)    → null:  cannot determine drift
        //   no active profile published    → false: device is not out of date with anything
        //   active profile exists          → compare; device reporting no version at all is
        //                                            by definition not running it (drift=true)
        Boolean profileDrift;
        if (degraded) {
            profileDrift = null;
        } else if (activeProfileVersion == null) {
            profileDrift = false;
        } else {
            profileDrift = !activeProfileVersion.equals(runningProfileVersion);
        }

        return new ProfileDriftResult(activeProfileVersion, profileDrift, degraded);
    }
}
