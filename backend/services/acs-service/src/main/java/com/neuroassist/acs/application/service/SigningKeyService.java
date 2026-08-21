package com.neuroassist.acs.application.service;

import com.neuroassist.acs.application.port.out.SigningKeyRepositoryPort;
import com.neuroassist.acs.domain.model.SigningKey;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Application service for RSA signing key lifecycle management (ACS-026, Item A1).
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>{@link #getOrCreateActiveKey()} — loads persisted key from DB or generates a new RSA-2048
 *       pair, persists it encrypted, and returns the live {@link RSAKey}
 *   <li>{@link #rotateIfExpired()} — rotates the active key if it is older than 7 days
 *   <li>{@link #getPublicKeys()} — returns all public keys for JWKS endpoint
 * </ul>
 *
 * <p>RULE-03: not @Transactional — key generation is infrastructure, not a business transaction.
 * SECURITY: private key material never appears in logs or in domain models.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SigningKeyService {

    private static final int RSA_KEY_SIZE = 2048;
    private static final long ROTATION_THRESHOLD_DAYS = 7;

    private final SigningKeyRepositoryPort signingKeyRepository;

    @Value("${acs.signing-key.encryption-secret}")
    private String encryptionSecret;

    /**
     * Returns the active RSA key pair. If no active key exists in the database, generates a new
     * RSA-2048 pair, persists it (private key encrypted with AES-256-GCM), and returns it.
     *
     * @return the active {@link RSAKey} with both public and private key material
     */
    public RSAKey getOrCreateActiveKey() {
        Optional<KeyPair> existingPair = signingKeyRepository.findActiveKeyPair(encryptionSecret);
        Optional<SigningKey> existingMeta = signingKeyRepository.findActiveKey();

        if (existingPair.isPresent() && existingMeta.isPresent()) {
            String kid = existingMeta.get().kid();
            log.info("Reusing persisted active signing key kid={}", kid);
            return buildRsaKey(kid, existingPair.get());
        }

        log.info("No active signing key found — generating new RSA-2048 key pair");
        KeyPair keyPair = generateRsaKeyPair();
        String kid = UUID.randomUUID().toString();

        signingKeyRepository.saveKeyPair(kid, keyPair, Instant.now(), encryptionSecret);
        log.info("New RSA signing key persisted kid={}", kid);

        return buildRsaKey(kid, keyPair);
    }

    /**
     * Rotates the active key if it is older than 7 days. Generates a new RSA-2048 pair, persists
     * it, and marks the old key as RETIRED. Noop if the active key is recent.
     */
    public void rotateIfExpired() {
        Optional<SigningKey> activeKey = signingKeyRepository.findActiveKey();
        if (activeKey.isEmpty()) {
            log.debug("rotateIfExpired: no active key — nothing to rotate");
            return;
        }

        SigningKey key = activeKey.get();
        Instant threshold = Instant.now().minus(ROTATION_THRESHOLD_DAYS, ChronoUnit.DAYS);

        if (key.notBefore() == null || !key.notBefore().isBefore(threshold)) {
            log.debug("rotateIfExpired: key kid={} is recent — no rotation needed", key.kid());
            return;
        }

        log.info("Rotating expired signing key kid={} (notBefore={})", key.kid(), key.notBefore());

        // Generate and persist new key
        KeyPair newPair = generateRsaKeyPair();
        String newKid = UUID.randomUUID().toString();
        signingKeyRepository.saveKeyPair(newKid, newPair, Instant.now(), encryptionSecret);

        // Retire old key
        signingKeyRepository.markRetired(key.id());

        log.info(
                "Signing key rotation complete: old kid={} RETIRED, new kid={} ACTIVE",
                key.kid(),
                newKid);
    }

    /**
     * Returns all public keys (ACTIVE + recently RETIRED) for the JWKS endpoint.
     *
     * @return list of {@link SigningKey} domain models (public key material only)
     */
    public List<SigningKey> getPublicKeys() {
        return signingKeyRepository.findPublicKeys();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    private RSAKey buildRsaKey(String kid, KeyPair keyPair) {
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(kid)
                .build();
    }
}
