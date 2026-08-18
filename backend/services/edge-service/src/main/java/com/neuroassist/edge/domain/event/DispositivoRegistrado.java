package com.neuroassist.edge.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: device successfully registered (enrollment token minted). Published in-process
 * after transaction commit via @TransactionalEventListener(AFTER_COMMIT). Zero Spring/JPA imports
 * (RULE-01, RULE-02).
 */
public record DispositivoRegistrado(UUID deviceId, String tenantRef, Instant at, String label) {

    public static DispositivoRegistrado of(UUID deviceId, String tenantRef, String label) {
        return new DispositivoRegistrado(deviceId, tenantRef, Instant.now(), label);
    }
}
