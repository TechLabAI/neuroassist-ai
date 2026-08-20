package com.neuroassist.hist.bdd;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Test-only Spring configuration that replaces the production {@link JwtDecoder} with an HMAC-256
 * decoder using a well-known test secret. This allows BDD tests (RANDOM_PORT) to create signed JWT
 * tokens without a live JWK Set endpoint.
 *
 * <p>Imported by {@link CucumberSpringContext} — not active in production.
 */
@TestConfiguration
public class BddJwtConfig {

    /**
     * 32-byte HMAC-256 test secret. Must be ≥ 256 bits for HS256. Identical key used in {@link
     * BddJwtTokenFactory} for token signing.
     */
    static final byte[] TEST_SECRET_BYTES =
            "neuroassist-hist-bdd-test-secret-32b!".getBytes(StandardCharsets.UTF_8);

    static final SecretKey TEST_SECRET_KEY = new SecretKeySpec(TEST_SECRET_BYTES, "HmacSHA256");

    /**
     * Overrides the auto-configured JwtDecoder (from jwk-set-uri) with an HMAC-256 decoder. The
     * {@code @Primary} ensures this bean wins over the auto-configured one.
     */
    @Bean
    @Primary
    public JwtDecoder bddJwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(TEST_SECRET_KEY)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
