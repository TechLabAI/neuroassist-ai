package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.api.dto.JwkSetResponse;
import com.neuroassist.acs.application.port.in.JwkSetUseCase;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWKS endpoint (ACS-019).
 *
 * <p>GET /.well-known/jwks.json — public signing keys for JWT validation (RFC 7517). This endpoint
 * is PUBLIC (no authentication required) and cached for 1 hour. SECURITY: only public key material
 * returned (never private exponent d). RULE-04: no infrastructure dependency here.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class JwkSetController {

    private final JwkSetUseCase jwkSetUseCase;

    /**
     * Returns the JWK Set with all active and recently retired public signing keys. Response is
     * cache-control: public, max-age=3600 (1 hour).
     *
     * @return 200 JwkSetResponse | 500 if no keys available (system misconfiguration)
     */
    @GetMapping({"/.well-known/jwks.json", "/oauth2/jwks"})
    public ResponseEntity<JwkSetResponse> getJwkSet() {
        log.debug("GET /.well-known/jwks.json");
        JwkSetResponse response = jwkSetUseCase.getPublicKeys();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .body(response);
    }
}
