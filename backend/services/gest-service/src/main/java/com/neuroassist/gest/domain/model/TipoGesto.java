package com.neuroassist.gest.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model for a facial-movement primitive (TipoGesto). ZERO Spring / ZERO JPA (RULE-01,
 * RULE-02). Multi-tenancy is implicit via schema routing; tenantId is carried in the domain object
 * for domain-service logic but is not stored as a column in the DB schema.
 *
 * <p>ADR-006 lifecycle: REGISTERED → PENDING_TRAINING → SUPPORTED → DEPRECATED (see
 * isTransitionAllowed).
 */
public record TipoGesto(
        UUID id,
        String nome,
        DetectabilityState detectabilityState,
        String engineFeatureRef,
        String tenantId,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy) {

    /**
     * Returns true when a transition from the current state to {@code to} is permitted by ADR-006.
     * The 5 allowed arcs are: REGISTERED→PENDING_TRAINING, REGISTERED→DEPRECATED,
     * PENDING_TRAINING→SUPPORTED, PENDING_TRAINING→DEPRECATED, SUPPORTED→DEPRECATED.
     */
    public boolean isTransitionAllowed(DetectabilityState to) {
        return switch (this.detectabilityState) {
            case REGISTERED -> to == DetectabilityState.PENDING_TRAINING;
            case PENDING_TRAINING ->
                    to == DetectabilityState.SUPPORTED || to == DetectabilityState.DEPRECATED;
            case SUPPORTED -> to == DetectabilityState.DEPRECATED;
            case DEPRECATED -> false;
        };
    }

    /**
     * Factory: create a new primitive in the initial REGISTERED state. Caller provides nome and
     * tenantId; id and timestamps are auto-generated.
     */
    public static TipoGesto register(String nome, String tenantId) {
        Instant now = Instant.now();
        return new TipoGesto(
                UUID.randomUUID(),
                nome,
                DetectabilityState.REGISTERED,
                null,
                tenantId,
                now,
                now,
                null,
                null);
    }

    /**
     * Returns a new TipoGesto with the state changed to {@code newState} and updatedAt refreshed.
     * Does NOT validate the transition — that is the responsibility of TipoGestoDomainService.
     */
    public TipoGesto withState(DetectabilityState newState, String updatedBy) {
        return new TipoGesto(
                this.id,
                this.nome,
                newState,
                this.engineFeatureRef,
                this.tenantId,
                this.createdAt,
                Instant.now(),
                this.createdBy,
                updatedBy);
    }

    public TipoGesto withState(
            DetectabilityState newState, String updatedBy, String newEngineFeatureRef) {
        return new TipoGesto(
                this.id,
                this.nome,
                newState,
                newEngineFeatureRef != null ? newEngineFeatureRef : this.engineFeatureRef,
                this.tenantId,
                this.createdAt,
                Instant.now(),
                this.createdBy,
                updatedBy);
    }
}
