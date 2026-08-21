package com.neuroassist.hist.domain.model;

/**
 * Thrown by {@code ActorDirectoryAdapter} when ACS is unreachable or the circuit breaker is open.
 * Signals fail-closed behaviour: the caller (HistoryQueryService) must never widen the data scope
 * by returning all records when ACS is unavailable (ADR-005, HIST-012).
 *
 * <p>Zero Spring/JPA imports (RULE-01, RULE-02). Mapped to HTTP 503 in {@code
 * GlobalExceptionHandler}.
 */
public class ActorDirectoryUnavailableException extends RuntimeException {

    public ActorDirectoryUnavailableException(String message) {
        super(message);
    }

    public ActorDirectoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
