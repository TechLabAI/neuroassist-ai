package com.neuroassist.gest.infrastructure.security;

/**
 * Raised when a service-identity token could not be obtained from the ACS (ADR service-identity,
 * defects C-18/C-19).
 *
 * <p><b>Fail-closed:</b> this exception must reach the caller. There is deliberately no fallback to
 * the caller's own JWT — that silent fallback is what turned the GEST 403 into a "degraded"
 * heartbeat instead of a visible failure.
 *
 * <p>LGPD/security: the message never carries the token nor the client secret.
 */
public class ServiceTokenUnavailableException extends RuntimeException {

    public ServiceTokenUnavailableException(String message) {
        super(message);
    }

    public ServiceTokenUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
