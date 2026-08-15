package com.neuroassist.gest.api.exception;

import com.neuroassist.gest.shared.error.CodedException;

/**
 * Thrown when attempting to regress from SUPPORTED to DEPRECATED without providing an explicit
 * reason (ADR-006 INVARIANT #4). Maps to HTTP 400 via GlobalExceptionHandler.
 */
public class RegressionReasonRequiredException extends CodedException {

    public RegressionReasonRequiredException() {
        super(
                "regression-reason-required",
                "Regression from SUPPORTED to DEPRECATED requires an explicit reason");
    }
}
