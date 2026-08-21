package com.neuroassist.hist.domain.model;

/**
 * Base exception for 400 Bad Request violations — structural request issues (e.g., incomplete
 * filter combination, invalid time window). Subclasses carry a stable {@link #errorCode()} that the
 * {@code GlobalExceptionHandler} maps to HTTP 400 + ProblemDetail. Zero Spring/JPA imports
 * (RULE-01, RULE-02).
 */
public class HistBadRequestException extends RuntimeException {

    private final String errorCode;

    public HistBadRequestException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** Stable error code corresponding to a {@code /errors/<code>} type URI in the API contract. */
    public String errorCode() {
        return errorCode;
    }
}
