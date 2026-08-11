package com.neuroassist.gest.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a TipoGesto primitive changes its detectability state (ADR-006).
 * Published via ApplicationEventPublisher after the transition is persisted (in-process). ZERO
 * Spring / ZERO JPA (RULE-01, RULE-02).
 */
public record PrimitiveTransitioned(
        UUID tipoGestoId, String fromState, String toState, String actor, Instant at) {}
