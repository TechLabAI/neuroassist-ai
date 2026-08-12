package com.neuroassist.gest.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a new TipoGesto primitive is registered (ADR-006). Published via
 * ApplicationEventPublisher after the entity is persisted (in-process). ZERO Spring / ZERO JPA
 * (RULE-01, RULE-02).
 */
public record PrimitiveRegistered(
        UUID tipoGestoId, String nome, String tenantRef, String actor, Instant at) {}
