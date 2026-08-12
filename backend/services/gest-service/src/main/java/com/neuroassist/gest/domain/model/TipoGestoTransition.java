package com.neuroassist.gest.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit record of a detectability state transition (invariant #4 — no silent
 * regression). ZERO Spring / ZERO JPA (RULE-01, RULE-02). Records are never updated or deleted by
 * the application.
 */
public record TipoGestoTransition(
        UUID id,
        UUID tipoGestoId,
        DetectabilityState fromState,
        DetectabilityState toState,
        String actor,
        String reason,
        Instant at) {

    /** Factory for creating a new transition record. The id and timestamp are auto-generated. */
    public static TipoGestoTransition of(
            UUID tipoGestoId,
            DetectabilityState fromState,
            DetectabilityState toState,
            String actor,
            String reason) {
        return new TipoGestoTransition(
                UUID.randomUUID(), tipoGestoId, fromState, toState, actor, reason, Instant.now());
    }
}
