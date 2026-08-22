package com.neuroassist.dash.infrastructure.security;

/**
 * Request body of {@code POST /oauth2/token/service} on acs-service (ACS-030).
 *
 * <p>Mirrors {@code com.neuroassist.acs.api.dto.ServiceTokenRequest} verbatim — see {@code
 * docs/architecture/backend/standards/service-identity.md} §2.
 *
 * <p>LGPD: {@code clientSecret} is request-only; it is never logged nor echoed.
 *
 * @param clientId identifier of this service, e.g. {@code dash-service}
 * @param clientSecret shared secret, validated by ACS against a BCrypt hash
 * @param tenantId tenant the emitted token is scoped to (required — downstream services route the
 *     Postgres {@code search_path} by the tenant claim)
 */
public record ServiceTokenRequest(String clientId, String clientSecret, String tenantId) {

    @Override
    public String toString() {
        // Defensive: keeps the secret out of any accidental string interpolation.
        return "ServiceTokenRequest[clientId=" + clientId + ", tenantId=" + tenantId + "]";
    }
}
