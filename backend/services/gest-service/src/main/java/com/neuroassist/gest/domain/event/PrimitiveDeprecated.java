package com.neuroassist.gest.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Specialised domain event for the DEPRECATED terminal state transition of a TipoGesto. Emitted
 * alongside PrimitiveTransitioned when toState==DEPRECATED (ADR-006). ZERO Spring / ZERO JPA
 * (RULE-01, RULE-02).
 */
public record PrimitiveDeprecated(UUID tipoGestoId, String nome, String actor, Instant at) {}
