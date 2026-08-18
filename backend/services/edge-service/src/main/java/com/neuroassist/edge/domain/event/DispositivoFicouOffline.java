package com.neuroassist.edge.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: device detected as offline (derived at read-time from staleness threshold, not from
 * a write). The listener for this event MUST only log or emit a metric — never attempt to write
 * state — because liveness is never stored and this event may fire on every liveness poll. Zero
 * Spring/JPA imports (RULE-01, RULE-02).
 */
public record DispositivoFicouOffline(
        UUID deviceId, String tenantRef, Instant at, Instant ultimaSincronizacao) {

    public static DispositivoFicouOffline of(
            UUID deviceId, String tenantRef, Instant ultimaSincronizacao) {
        return new DispositivoFicouOffline(deviceId, tenantRef, Instant.now(), ultimaSincronizacao);
    }
}
