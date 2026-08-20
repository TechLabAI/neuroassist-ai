package com.neuroassist.hist.domain.model;

import java.util.UUID;

/**
 * Thrown when the {@code correctsFactId} in a correction record cannot be resolved to an existing
 * audit fact in the same tenant. Maps to HTTP 422 / {@code type: /errors/corrects-target-not-found}.
 */
public class CorrectTargetNotFoundException extends HistDomainException {

    public CorrectTargetNotFoundException(UUID correctsFactId) {
        super(
                "corrects-target-not-found",
                "correctsFactId '"
                        + correctsFactId
                        + "' does not reference any existing audit fact in this tenant.");
    }
}
