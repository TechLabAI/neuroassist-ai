package com.neuroassist.hist.domain.model;

import java.time.Instant;

/**
 * Thrown when the {@code from} timestamp is after the {@code to} timestamp in a history query.
 * Maps to HTTP 400 / {@code type: /errors/invalid-time-window}.
 * Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public class InvalidTimeWindowException extends RuntimeException {

    public InvalidTimeWindowException(Instant from, Instant to) {
        super("Time window is invalid: from '" + from + "' is after to '" + to + "'.");
    }

    public String errorCode() {
        return "invalid-time-window";
    }
}
