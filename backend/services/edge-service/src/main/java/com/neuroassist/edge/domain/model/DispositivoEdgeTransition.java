package com.neuroassist.edge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit of pairing-lifecycle and liveness transitions (ADR-005 invariant #3). Liveness
 * rows capture only changes (online↔offline), never every heartbeat. Zero Spring/JPA imports
 * (RULE-01, RULE-02).
 */
public record DispositivoEdgeTransition(
        UUID id,
        String tenantId,
        UUID dispositivoEdgeId,
        TransitionKind kind,
        String fromState,
        String toState,
        String actor,
        String reason,
        Instant at) {

    public static DispositivoEdgeTransition pairing(
            UUID deviceId,
            String tenantId,
            String fromState,
            String toState,
            String actor,
            String reason) {
        return new DispositivoEdgeTransition(
                UUID.randomUUID(),
                tenantId,
                deviceId,
                TransitionKind.pairing,
                fromState,
                toState,
                actor,
                reason,
                Instant.now());
    }

    public static DispositivoEdgeTransition liveness(
            UUID deviceId, String tenantId, String fromState, String toState) {
        return new DispositivoEdgeTransition(
                UUID.randomUUID(),
                tenantId,
                deviceId,
                TransitionKind.liveness,
                fromState,
                toState,
                "system",
                null,
                Instant.now());
    }
}
