package com.neuroassist.acs.application.port.in;

import com.neuroassist.acs.api.dto.JwkSetResponse;

/**
 * Inbound port — JWK Set use case. Exposes public RSA keys for JWT validation by other services.
 * RFC 7517 compliant — no private key material exposed.
 */
public interface JwkSetUseCase {

    /**
     * Returns all active and recently retired public signing keys (JWK Set format).
     *
     * @return JwkSetResponse with RFC 7517 key list
     */
    JwkSetResponse getPublicKeys();
}
