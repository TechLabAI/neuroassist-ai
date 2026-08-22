package com.neuroassist.dash.infrastructure.security;

/**
 * Raised when a service-identity token cannot be obtained from acs-service.
 *
 * <p>Fail-closed by design (service-identity.md §5): callers must NOT fall back to the caller's
 * token. In dash-service the only consumer is the hist-service Feign client, whose adapter ({@code
 * HistFactAdapter}) already owns a resilient contract — the fact stays {@code pending} in the
 * outbox and is retried later.
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
