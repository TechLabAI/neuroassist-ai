package com.neuroassist.pac.shared.error;

/**
 * Thrown by {@link com.neuroassist.pac.application.port.out.EmergencyEligibilityGate} when the GEST
 * service is unreachable or the circuit breaker is OPEN during an emergency publish.
 *
 * <p>ADR-005 (fail-closed): for {@code urgencia=emergencia}, any failure to confirm detectability
 * MUST block the publish and surface this error (HTTP 503 + Retry-After header).
 *
 * <p>The {@link com.neuroassist.pac.api.exception.GlobalExceptionHandler} maps this exception to
 * HTTP 503 with a {@code Retry-After} header of {@link #retryAfterSeconds()} seconds.
 */
public class EligibilityGateUnavailableException extends RuntimeException {

    private final int retryAfterSeconds;

    public EligibilityGateUnavailableException(int retryAfterSeconds, String message) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public EligibilityGateUnavailableException(
            int retryAfterSeconds, String message, Throwable cause) {
        super(message, cause);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
