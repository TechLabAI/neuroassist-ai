package com.neuroassist.edge.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: detection-profile version mismatch detected — the device's running version differs
 * from the current version advertised by GEST. Emitted only when drift state CHANGES (new drift
 * detected, not repeated for every heartbeat). Published in-process after transaction commit
 * via @TransactionalEventListener(AFTER_COMMIT). Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public record ProfileDriftDetected(
        UUID deviceId, String tenantRef, Instant at, Long runningVersion, Long currentVersion) {

    public static ProfileDriftDetected of(
            UUID deviceId, String tenantRef, Long runningVersion, Long currentVersion) {
        return new ProfileDriftDetected(
                deviceId, tenantRef, Instant.now(), runningVersion, currentVersion);
    }
}
