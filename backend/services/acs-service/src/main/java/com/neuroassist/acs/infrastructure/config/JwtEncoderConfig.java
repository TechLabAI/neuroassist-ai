package com.neuroassist.acs.infrastructure.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Configures RSA-based JWT encoder and decoder for self-issued tokens.
 *
 * <p>P2 behaviour: generates a transient RSA-2048 key pair at startup if no persisted key is
 * configured. In P3+ the key pair will be loaded from the signing_key table
 * (SigningKeyJpaEntity.privateKeyEnc + publicJwk) via SigningKeyPortAdapter.
 *
 * <p>The JwtDecoder is configured with the same RSA public key so acs-service validates its own
 * tokens in the ResourceServer filter chain (SecurityConfig).
 */
@Slf4j
@Configuration
public class JwtEncoderConfig {

    /** Singleton RSA key pair for this JVM instance (P2: transient). */
    private final RSAKey rsaKey;

    public JwtEncoderConfig() {
        this.rsaKey = generateRsaKey();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        var jwkSet = new JWKSet(rsaKey);
        ImmutableJWKSet<SecurityContext> jwkSource = new ImmutableJWKSet<>(jwkSet);
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder() throws JOSEException {
        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.toPublicKey();
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    /**
     * Exposes the RSA key (public + private) for use by SigningKeyPortAdapter and JwkSetService.
     * Only the public part is ever serialized to responses.
     */
    @Bean
    public RSAKey signingRsaKey() {
        return rsaKey;
    }

    private RSAKey generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            String kid = UUID.randomUUID().toString();
            log.warn(
                    "JWT: using transient RSA key pair (kid={}) — replace with persisted key in production",
                    kid);
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(kid)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair for JWT signing", e);
        }
    }
}
