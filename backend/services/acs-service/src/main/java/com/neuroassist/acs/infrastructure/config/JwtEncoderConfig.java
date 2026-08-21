package com.neuroassist.acs.infrastructure.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Configures RSA-based JWT encoder and decoder backed by the persisted signing key (ACS-026, Item
 * A2).
 *
 * <p>P7 behaviour: delegates to {@link SigningKeyHolder} for the active RSA key pair. The holder is
 * populated by {@link SigningKeyInitializer} (ApplicationRunner) after the full Spring context
 * including JPA is ready. The JwtEncoder and JwtDecoder resolve the key lazily on first use, so
 * there is no circular initialization dependency.
 *
 * <p>SECURITY: private key material is only accessed via the holder; it never appears in
 * configuration files, logs, or API responses.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JwtEncoderConfig {

    private final SigningKeyHolder signingKeyHolder;

    /**
     * JWT encoder backed by a dynamic JWK source that reads the active key from {@link
     * SigningKeyHolder} on each encode call. Returns an empty JWK set if the holder is not yet
     * initialized (pre-startup phase).
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        // Dynamic JWKSource: resolves the active key from SigningKeyHolder at encode time
        var dynamicSource =
                (com.nimbusds.jose.jwk.source.JWKSource<SecurityContext>)
                        (jwkSelector, context) -> {
                            RSAKey activeKey = signingKeyHolder.getActiveKeyOrNull();
                            if (activeKey == null) {
                                log.warn(
                                        "JwtEncoder: SigningKeyHolder not yet initialized — returning empty JWK set");
                                return List.of();
                            }
                            return jwkSelector.select(new JWKSet(activeKey));
                        };
        return new NimbusJwtEncoder(dynamicSource);
    }

    /**
     * JWT decoder that validates tokens against the active public key in {@link SigningKeyHolder}.
     * Resolved lazily on each decode call.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            RSAKey activeKey = signingKeyHolder.getActiveKey();
            try {
                RSAPublicKey publicKey = (RSAPublicKey) activeKey.toPublicKey();
                return NimbusJwtDecoder.withPublicKey(publicKey).build().decode(token);
            } catch (JOSEException e) {
                throw new BadJwtException("Failed to extract RSA public key for JWT decoding", e);
            }
        };
    }
}
