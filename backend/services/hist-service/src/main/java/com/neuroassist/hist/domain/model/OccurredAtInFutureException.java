package com.neuroassist.hist.domain.model;

import java.time.Instant;

/**
 * Thrown when the submitted {@code occurredAt} timestamp is more than 5 minutes ahead of the
 * service clock (clock-drift guard). Maps to HTTP 422 / {@code type:
 * /errors/occurred-at-in-future}. Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public class OccurredAtInFutureException extends HistDomainException {

    public OccurredAtInFutureException(Instant occurredAt) {
        super(
                "future-occurred-at",
                "occurredAt '"
                        + occurredAt
                        + "' is more than 5 minutes in the future — clock drift exceeded.");
    }
}
