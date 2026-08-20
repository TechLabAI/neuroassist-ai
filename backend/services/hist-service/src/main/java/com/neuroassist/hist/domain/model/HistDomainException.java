package com.neuroassist.hist.domain.model;

/**
 * Base exception for domain-level violations in {@code hist-service}. Subclasses carry a stable
 * {@link #errorCode()} that the {@code GlobalExceptionHandler} maps to the appropriate HTTP status
 * and {@code application/problem+json} response. Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public class HistDomainException extends RuntimeException {

    private final String errorCode;

    public HistDomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** Stable error code corresponding to a {@code /errors/<code>} type URI in the API contract. */
    public String errorCode() {
        return errorCode;
    }
}
