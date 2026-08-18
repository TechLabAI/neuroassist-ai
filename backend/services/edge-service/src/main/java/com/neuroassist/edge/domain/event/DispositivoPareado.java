package com.neuroassist.edge.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: device successfully paired with a patient. Published in-process after transaction
 * commit via @TransactionalEventListener(AFTER_COMMIT). Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public record DispositivoPareado(
        UUID deviceId, String tenantRef, Instant at, UUID pacienteId, String actor) {

    public static DispositivoPareado of(
            UUID deviceId, String tenantRef, UUID pacienteId, String actor) {
        return new DispositivoPareado(deviceId, tenantRef, Instant.now(), pacienteId, actor);
    }
}
