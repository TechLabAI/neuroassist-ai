package com.neuroassist.gest.api.exception;

import com.neuroassist.gest.shared.error.CodedException;
import java.util.Map;

/**
 * Thrown when a requested detectability state transition is not permitted by the ADR-006 lifecycle.
 * Maps to HTTP 409 via GlobalExceptionHandler (error code: illegal-transition).
 *
 * <p>Allowed arcs (ADR-006): REGISTERED → PENDING_TRAINING | DEPRECATED PENDING_TRAINING →
 * SUPPORTED | DEPRECATED SUPPORTED → DEPRECATED
 */
public class IllegalTransitionException extends CodedException {

    public IllegalTransitionException(String fromState, String toState) {
        super(
                "illegal-transition",
                "Transition from '"
                        + fromState
                        + "' to '"
                        + toState
                        + "' is not permitted by ADR-006");
        withContext(Map.of("fromState", fromState, "toState", toState));
    }
}
