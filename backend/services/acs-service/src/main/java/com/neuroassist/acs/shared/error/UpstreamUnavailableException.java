package com.neuroassist.acs.shared.error;

/**
 * Thrown when an upstream service (MIG, PAC, HIST) is unavailable. Maps to HTTP 502. Resilience4j
 * circuit breaker enforced at adapter level.
 */
public class UpstreamUnavailableException extends CodedException {

    public UpstreamUnavailableException(String service, Throwable cause) {
        super("acs.upstream-unavailable", "Upstream service unavailable: " + service, cause);
    }
}
