package com.neuroassist.hist.domain.model;

import java.util.UUID;

/**
 * Thrown when {@code correctsFactId} in the ingestion request references a factId that does not
 * exist in the same tenant. Maps to HTTP 422 / {@code type: /errors/corrects-target-not-found}.
 * Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public class CorrectsTargetNotFoundException extends HistDomainException {

    public CorrectsTargetNotFoundException(UUID correctsFactId) {
        super(
                "corrects-target-not-found",
                "The fact referenced by correctsFactId '"
                        + correctsFactId
                        + "' does not exist in this tenant.");
    }
}
