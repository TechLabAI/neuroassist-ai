package com.neuroassist.acs.infrastructure.persistence.adapter;

import com.neuroassist.acs.application.port.out.SigningKeyPort;
import com.neuroassist.acs.domain.model.SigningKey;
import com.neuroassist.acs.domain.model.SigningKeyStatus;
import com.neuroassist.acs.infrastructure.config.SigningKeyHolder;
import com.neuroassist.acs.infrastructure.persistence.repository.SigningKeyJpaRepository;
import com.neuroassist.acs.shared.error.NoActiveSigningKeyException;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing SigningKeyPort using the persisted RSA key from SigningKeyHolder (ACS-026,
 * Item A4).
 *
 * <p>P7: uses SigningKeyHolder (populated by SigningKeyInitializer from DB) instead of the
 * in-memory transient RSAKey from JwtEncoderConfig (P2). The holder is updated after startup and
 * during key rotation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SigningKeyPortAdapter implements SigningKeyPort {

    private final SigningKeyHolder signingKeyHolder;
    private final SigningKeyJpaRepository signingKeyJpaRepository;

    @Override
    public SigningKey getActiveSigningKey() {
        try {
            RSAKey activeKey = signingKeyHolder.getActiveKey();
            return buildSigningKey(activeKey);
        } catch (Exception e) {
            log.error("Failed to get active signing key from holder: {}", e.getMessage());
            throw new NoActiveSigningKeyException();
        }
    }

    @Override
    public List<SigningKey> getPublicKeys() {
        try {
            return signingKeyJpaRepository
                    .findByStatusIn(List.of(SigningKeyStatus.ACTIVE, SigningKeyStatus.RETIRED))
                    .stream()
                    .map(
                            entity ->
                                    new SigningKey(
                                            entity.getId(),
                                            entity.getKid(),
                                            entity.getAlgorithm(),
                                            entity.getPublicJwk(),
                                            entity.getStatus(),
                                            entity.getNotBefore() != null
                                                    ? entity.getNotBefore()
                                                            .toInstant(ZoneOffset.UTC)
                                                    : null,
                                            entity.getRetiredAt() != null
                                                    ? entity.getRetiredAt()
                                                            .toInstant(ZoneOffset.UTC)
                                                    : null))
                    .toList();
        } catch (Exception e) {
            log.warn(
                    "Could not load public keys from DB: {} — using holder fallback",
                    e.getMessage());
            RSAKey activeKey = signingKeyHolder.getActiveKeyOrNull();
            if (activeKey == null) return List.of();
            try {
                return List.of(buildSigningKey(activeKey));
            } catch (Exception ex) {
                log.warn("Could not build signing key from holder: {}", ex.getMessage());
                return List.of();
            }
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
