package com.neuroassist.gest.api.exception;

import com.neuroassist.gest.shared.error.CodedException;

/**
 * Thrown when transitioning to SUPPORTED without providing an engineFeatureRef (ADR-006 INVARIANT
 * #5). Maps to HTTP 422 via GlobalExceptionHandler.
 */
public class EngineFeatureRefRequiredException extends CodedException {

    public EngineFeatureRefRequiredException() {
        super(
                "engine-feature-ref-required",
                "Transition to SUPPORTED requires a non-null engineFeatureRef");
    }
}
