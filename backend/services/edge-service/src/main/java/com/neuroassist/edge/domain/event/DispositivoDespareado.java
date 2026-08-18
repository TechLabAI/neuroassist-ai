package com.neuroassist.edge.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: device unpaired from its patient. Published in-process after transaction commit
 * via @TransactionalEventListener(AFTER_COMMIT). Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public record DispositivoDespareado(
        UUID deviceId, String tenantRef, Instant at, String reason, String actor) {

    public static DispositivoDespareado of(
            UUID deviceId, String tenantRef, String reason, String actor) {
        return new DispositivoDespareado(deviceId, tenantRef, Instant.now(), reason, actor);
    }
}
