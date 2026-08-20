package com.neuroassist.acs.infrastructure.persistence.adapter;

import com.neuroassist.acs.application.port.out.SigningKeyRepositoryPort;
import com.neuroassist.acs.domain.model.SigningKey;
import com.neuroassist.acs.domain.model.SigningKeyStatus;
import com.neuroassist.acs.infrastructure.persistence.entity.SigningKeyJpaEntity;
import com.neuroassist.acs.infrastructure.persistence.repository.SigningKeyJpaRepository;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter for SigningKeyRepositoryPort (CRUD operations on neuroassist_global.signing_key). Full
 * implementation including save(), saveKeyPair(), findActiveKeyPair(), markRetired() (ACS-011/P3,
 * ACS-026/P7). SECURITY: privateKeyEnc is encrypted with AES-256-GCM and never exposed in domain
 * models.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SigningKeyRepositoryAdapter implements SigningKeyRepositoryPort {

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final byte[] FIXED_SALT =
            "acs-signing-key-salt-v1".getBytes(StandardCharsets.UTF_8);
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SigningKeyJpaRepository repository;

    @Override
    public SigningKey save(SigningKey entity) {
        SigningKeyJpaEntity jpaEntity = toEntity(entity);
        SigningKeyJpaEntity saved = repository.save(jpaEntity);
        return toDomain(saved);
    }

    @Override
    public Optional<SigningKey> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<SigningKey> findActiveKey() {
        return repository
                .findFirstByStatusOrderByNotBeforeDesc(SigningKeyStatus.ACTIVE)
                .map(this::toDomain);
    }

    @Override
    public List<SigningKey> findPublicKeys() {
        return repository
                .findByStatusIn(List.of(SigningKeyStatus.ACTIVE, SigningKeyStatus.RETIRED))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public SigningKey saveKeyPair(
            String kid, KeyPair keyPair, Instant notBefore, String encryptionSecret) {
        byte[] encryptedPrivKey =
                encryptPrivateKey((RSAPrivateKey) keyPair.getPrivate(), encryptionSecret);
        String publicJwk = buildPublicJwk(kid, (RSAPublicKey) keyPair.getPublic());

        SigningKeyJpaEntity entity = new SigningKeyJpaEntity();
        entity.setKid(kid);
        entity.setAlgorithm("RS256");
        entity.setPublicJwk(publicJwk);
        entity.setPrivateKeyEnc(encryptedPrivKey);
        entity.setStatus(SigningKeyStatus.ACTIVE);
        entity.setNotBefore(LocalDateTime.ofInstant(notBefore, ZoneOffset.UTC));
        entity.setCriadoEm(LocalDateTime.now(ZoneOffset.UTC));

        SigningKeyJpaEntity saved = repository.save(entity);
        log.debug("Persisted signing key pair kid={}", kid);
        return toDomain(saved);
    }

    @Override
    public Optional<KeyPair> findActiveKeyPair(String encryptionSecret) {
        return repository
                .findFirstByStatusOrderByNotBeforeDesc(SigningKeyStatus.ACTIVE)
                .flatMap(entity -> Optional.ofNullable(decryptKeyPair(entity, encryptionSecret)));
    }

    @Override
    public void markRetired(UUID id) {
        repository
                .findById(id)
                .ifPresent(
                        entity -> {
                            entity.setStatus(SigningKeyStatus.RETIRED);
                            entity.setRetiredAt(LocalDateTime.now(ZoneOffset.UTC));
                            entity.setAtualizadoEm(LocalDateTime.now(ZoneOffset.UTC));
                            repository.save(entity);
                            log.info("Signing key kid={} marked as RETIRED", entity.getKid());
                        });
    }

    // -----------------------------------------------------------------------
    // Private: AES-256-GCM encryption helpers
    // -----------------------------------------------------------------------

    private byte[] encryptPrivateKey(RSAPrivateKey privateKey, String secret) {
        try {
            SecretKey aesKey = deriveAesKey(secret);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
            byte[] ciphertext = cipher.doFinal(privateKey.getEncoded());

            // Prepend IV to ciphertext: [12-byte IV | ciphertext+tag]
            byte[] result = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, GCM_IV_LENGTH, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt RSA private key", e);
        }
    }

    private KeyPair decryptKeyPair(SigningKeyJpaEntity entity, String secret) {
        try {
            byte[] encryptedData = entity.getPrivateKeyEnc();
            if (encryptedData == null || encryptedData.length <= GCM_IV_LENGTH) {
                log.warn(
                        "No encrypted private key for kid={} — key may be legacy transient",
                        entity.getKid());
                return null;
            }

            SecretKey aesKey = deriveAesKey(secret);
            byte[] iv = Arrays.copyOfRange(encryptedData, 0, GCM_IV_LENGTH);
            byte[] ciphertext =
                    Arrays.copyOfRange(encryptedData, GCM_IV_LENGTH, encryptedData.length);

            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);
            byte[] privateKeyBytes = cipher.doFinal(ciphertext);

            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(spec);

            // Reconstruct public key from stored publicJwk
            RSAKey rsaKey = RSAKey.parse(entity.getPublicJwk());
            PublicKey publicKey = rsaKey.toPublicKey();

            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            log.error(
                    "Failed to decrypt private key for kid={}: {}",
                    entity.getKid(),
                    e.getMessage());
            return null;
        }
    }

    private SecretKey deriveAesKey(String secret) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        PBEKeySpec spec =
                new PBEKeySpec(secret.toCharArray(), FIXED_SALT, PBKDF2_ITERATIONS, AES_KEY_BITS);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private String buildPublicJwk(String kid, RSAPublicKey publicKey) {
        try {
            return new RSAKey.Builder(publicKey).keyID(kid).build().toPublicJWK().toJSONString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build public JWK string for kid=" + kid, e);
        }
    }

    // -----------------------------------------------------------------------
    // Private: entity mapping
    // -----------------------------------------------------------------------

    private SigningKeyJpaEntity toEntity(SigningKey key) {
        SigningKeyJpaEntity entity = new SigningKeyJpaEntity();
        if (key.id() != null) {
            entity.setId(key.id());
        }
        entity.setKid(key.kid());
        entity.setAlgorithm(key.algorithm() != null ? key.algorithm() : "RS256");
        entity.setPublicJwk(key.publicJwk());
        entity.setPrivateKeyEnc(new byte[0]);
        entity.setStatus(key.status());
        entity.setNotBefore(
                key.notBefore() != null
                        ? LocalDateTime.ofInstant(key.notBefore(), ZoneOffset.UTC)
                        : null);
        entity.setRetiredAt(
                key.retiredAt() != null
                        ? LocalDateTime.ofInstant(key.retiredAt(), ZoneOffset.UTC)
                        : null);
        entity.setCriadoEm(LocalDateTime.now(ZoneOffset.UTC));
        return entity;
    }

    private SigningKey toDomain(SigningKeyJpaEntity entity) {
        // SECURITY: privateKeyEnc is NOT included in the domain model (RULE-02)
        return new SigningKey(
                entity.getId(),
                entity.getKid(),
                entity.getAlgorithm(),
                entity.getPublicJwk(),
                entity.getStatus(),
                entity.getNotBefore() != null
                        ? entity.getNotBefore().toInstant(ZoneOffset.UTC)
                        : null,
                entity.getRetiredAt() != null
                        ? entity.getRetiredAt().toInstant(ZoneOffset.UTC)
                        : null);
    }
}
