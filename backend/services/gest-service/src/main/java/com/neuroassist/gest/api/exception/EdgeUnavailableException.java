package com.neuroassist.gest.api.exception;

import com.neuroassist.gest.shared.error.CodedException;

/**
 * Thrown when the edge service is unreachable or returns an unexpected error.
 *
 * <p>Maps to HTTP 503 via {@link GlobalExceptionHandler} (error code: {@code edge-unavailable}).
 * For use in application-layer services; the infrastructure adapter throws {@link CodedException}
 * directly to comply with RULE-05.
 */
public class EdgeUnavailableException extends CodedException {

    public EdgeUnavailableException(String reason) {
        super("edge-unavailable", "Edge service unavailable: " + reason);
    }

    public EdgeUnavailableException(Throwable cause) {
        super(
                "edge-unavailable",
                "Edge service unavailable: " + (cause != null ? cause.getMessage() : "unknown"),
                cause);
    }
}
