package com.neuroassist.gest.infrastructure.security;

/**
 * Outbound seam for {@code POST /oauth2/token/service} on the ACS (service-identity contract).
 *
 * <p>Kept as an interface so {@link ServiceTokenProvider} (cache + renewal policy) can be unit
 * tested without HTTP.
 */
public interface ServiceTokenClient {

    /**
     * Requests a service-identity token scoped to {@code tenantId}.
     *
     * @param tenantId tenant the token is scoped to (never null/blank)
     * @return the ACS response; implementations throw on any transport/HTTP error
     */
    ServiceTokenResponse requestToken(String tenantId);
}
