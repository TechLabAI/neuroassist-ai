package com.neuroassist.evnt.infrastructure.security;

/**
 * Raised when a service-identity token cannot be obtained from acs-service.
 *
 * <p>Fail-closed by design (service-identity.md §5): callers must NOT fall back to the caller's
 * token. In evnt-service every outbound call uses service identity, and each adapter decides how to
 * absorb the failure according to its own posture: GEST validation and ACS routing are fail-closed
 * (the report is rejected), while DASH dispatch and HIST audit are fail-open (the dispatch stays
 * {@code QUEUED} and the fact is dropped with a warning) — see plan-e2e-integration.md §4, NTF-04
 * and AUD-10.
 *
 * <p>LGPD: the message never carries the client secret nor the token value.
 */
public class ServiceTokenUnavailableException extends RuntimeException {

    public ServiceTokenUnavailableException(String message) {
        super(message);
    }

    public ServiceTokenUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
