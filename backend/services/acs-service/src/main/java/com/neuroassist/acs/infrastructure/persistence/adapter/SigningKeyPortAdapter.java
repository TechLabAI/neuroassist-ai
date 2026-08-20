package com.neuroassist.acs.infrastructure.persistence.adapter;

import com.neuroassist.acs.application.port.out.SigningKeyPort;
import com.neuroassist.acs.domain.model.SigningKey;
import com.neuroassist.acs.domain.model.SigningKeyStatus;
import com.neuroassist.acs.shared.error.NoActiveSigningKeyException;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing SigningKeyPort using the transient RSA key from JwtEncoderConfig.
 *
 * <p>P2: uses the in-memory RSAKey bean. P3 (ACS-011) will replace this with a DB-backed
 * implementation reading from signing_key table via SigningKeyJpaRepository.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SigningKeyPortAdapter implements SigningKeyPort {

    private final RSAKey signingRsaKey;

    @Override
    public SigningKey getActiveSigningKey() {
        try {
            return buildSigningKey(signingRsaKey);
        } catch (Exception e) {
            log.error("Failed to build active signing key from RSA key: {}", e.getMessage());
            throw new NoActiveSigningKeyException();
        }
    }

    @Override
    public List<SigningKey> getPublicKeys() {
        try {
            return List.of(buildSigningKey(signingRsaKey));
        } catch (Exception e) {
            log.warn("Could not build public key list: {}", e.getMessage());
            return List.of();
        }
    }

    private SigningKey buildSigningKey(RSAKey rsaKey) throws Exception {
        return new SigningKey(
                null,
                rsaKey.getKeyID(),
                "RS256",
                rsaKey.toPublicJWK().toJSONString(),
                SigningKeyStatus.ACTIVE,
                Instant.now(),
                null);
    }
}
