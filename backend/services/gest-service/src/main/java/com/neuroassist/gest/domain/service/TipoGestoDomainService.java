package com.neuroassist.gest.domain.service;

import com.neuroassist.gest.api.exception.IllegalTransitionException;
import com.neuroassist.gest.domain.model.DetectabilityState;

/**
 * Domain service for TipoGesto invariants (ADR-006). Pure POJO — zero Spring imports (RULE-01).
 * Registered as @Bean in DomainServiceConfig.
 *
 * <p>Allowed ADR-006 transition arcs: REGISTERED → PENDING_TRAINING | DEPRECATED PENDING_TRAINING →
 * SUPPORTED | DEPRECATED SUPPORTED → DEPRECATED DEPRECATED → (nothing — terminal state)
 */
public class TipoGestoDomainService {

    /**
     * Validates that a transition from {@code from} to {@code to} is permitted by ADR-006.
     *
     * @param from current state
     * @param to target state
     * @throws IllegalTransitionException if the transition is not one of the 5 allowed arcs
     */
    public void validateTransition(DetectabilityState from, DetectabilityState to) {
        boolean allowed =
                switch (from) {
                    case REGISTERED ->
                            to == DetectabilityState.PENDING_TRAINING
                                    || to == DetectabilityState.DEPRECATED;
                    case PENDING_TRAINING ->
                            to == DetectabilityState.SUPPORTED
                                    || to == DetectabilityState.DEPRECATED;
                    case SUPPORTED -> to == DetectabilityState.DEPRECATED;
                    case DEPRECATED -> false;
                };
        if (!allowed) {
            throw new IllegalTransitionException(from.name(), to.name());
        }
    }
}
