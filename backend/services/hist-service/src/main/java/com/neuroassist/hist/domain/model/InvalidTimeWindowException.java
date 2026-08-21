package com.neuroassist.hist.domain.model;

import java.time.Instant;

/**
 * Thrown when the {@code from} timestamp is after {@code to} (inverted time window). Maps to HTTP
 * 400 / {@code type: /errors/invalid-time-window}. Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public class InvalidTimeWindowException extends HistBadRequestException {

    public InvalidTimeWindowException(Instant from, Instant to) {
        super(
                "invalid-time-window",
                "'from' ("
                        + from
                        + ") must be before 'to' ("
                        + to
                        + ") — time window is inverted.");
    }
}
