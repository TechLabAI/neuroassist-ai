package com.neuroassist.pac.api.dto;

/**
 * Request body for lifecycle action endpoints.
 *
 * <p>Lifecycle transitions (:validate, :publish, :retire) are identified by the path — no body is
 * required for the standard transitions. This record is kept for extensibility (e.g. a future audit
 * note or idempotency key in the body).
 */
public record ComandoLifecycleRequest() {}
