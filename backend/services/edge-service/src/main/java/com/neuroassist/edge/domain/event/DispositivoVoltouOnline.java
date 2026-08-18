package com.neuroassist.edge.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: device transitioned from offline to online (liveness crossing detected on
 * heartbeat). Published in-process after transaction commit
 * via @TransactionalEventListener(AFTER_COMMIT). Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public record DispositivoVoltouOnline(UUID deviceId, String tenantRef, Instant at) {

    public static DispositivoVoltouOnline of(UUID deviceId, String tenantRef) {
        return new DispositivoVoltouOnline(deviceId, tenantRef, Instant.now());
    }
}
