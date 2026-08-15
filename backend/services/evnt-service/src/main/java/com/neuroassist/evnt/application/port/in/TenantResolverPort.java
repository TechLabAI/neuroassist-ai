package com.neuroassist.evnt.application.port.in;

/**
 * Inbound port: access to current request tenant context (RULE-07).
 *
 * <p>Abstracts TenantContextHolder to preserve RULE-04 (api.* must not depend on infrastructure.*).
 */
public interface TenantResolverPort {

    /**
     * Returns the current tenant ID from the request context.
     *
     * @throws IllegalStateException if tenant context is not set
     */
    String getTenantIdRequired();

    /** Returns the current tenant ID, or null if not set. */
    String getTenantIdOrNull();
}
