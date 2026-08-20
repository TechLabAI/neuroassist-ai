package com.neuroassist.acs.application.service;

import com.neuroassist.acs.api.dto.JwkSetResponse;
import com.neuroassist.acs.application.port.in.JwkSetUseCase;
import com.neuroassist.acs.application.port.out.SigningKeyPort;
import com.neuroassist.acs.domain.model.SigningKey;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Returns the JWKS endpoint content (RFC 7517 JWK Set) for JWT validation by other services.
 *
 * <p>ACS-019. SECURITY: only public key material (n, e) returned — never private exponent (d).
 * RULE-03: no @Transactional needed (read-only, no DB write).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwkSetService implements JwkSetUseCase {

    private final SigningKeyPort signingKeyPort;

    /**
     * Returns all active and recently retired public signing keys in RFC 7517 JWK Set format.
     *
     * @return JwkSetResponse with keys array (public RSA keys only)
     */
    @Override
    public JwkSetResponse getPublicKeys() {
        List<SigningKey> keys = signingKeyPort.getPublicKeys();

        List<Map<String, Object>> jwkKeys = keys.stream().map(this::toJwkMap).toList();

        log.debug("Returning {} public keys from JWKS endpoint", jwkKeys.size());
        return new JwkSetResponse(jwkKeys);
    }

    /**
     * Converts a SigningKey domain model to a RFC 7517 JWK map. The publicJwk field is stored as a
     * JSON string — parse it into a Map. SECURITY: only public fields are included (kty, use, alg,
     * kid, n, e).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toJwkMap(SigningKey key) {
        try {
            // publicJwk is a Nimbus RSAKey.toPublicJWK().toJSONString() output
            // We parse it back into a Map for RFC 7517 serialization
            com.nimbusds.jose.jwk.RSAKey rsaKey =
                    com.nimbusds.jose.jwk.RSAKey.parse(key.publicJwk());

            return Map.of(
                    "kty",
                    "RSA",
                    "use",
                    "sig",
                    "alg",
                    key.algorithm() != null ? key.algorithm() : "RS256",
                    "kid",
                    key.kid(),
                    "n",
                    rsaKey.getModulus().toString(),
                    "e",
                    rsaKey.getPublicExponent().toString());
        } catch (Exception e) {
            log.warn("Failed to parse public JWK for kid={}: {}", key.kid(), e.getMessage());
            // Return minimal safe representation
            return Map.of(
                    "kty", "RSA",
                    "use", "sig",
                    "alg", "RS256",
                    "kid", key.kid());
        }
    }
}
