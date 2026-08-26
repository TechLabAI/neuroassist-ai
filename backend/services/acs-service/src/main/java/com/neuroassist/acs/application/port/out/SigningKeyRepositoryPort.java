package com.neuroassist.acs.application.port.out;

import com.neuroassist.acs.domain.model.SigningKey;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — SigningKey CRUD repository (neuroassist_global schema). Zero Spring/JPA imports
 * (RULE-01, RULE-02).
 */
public interface SigningKeyRepositoryPort {

    SigningKey save(SigningKey entity);

    Optional<SigningKey> findById(UUID id);

    /**
     * Returns the currently active signing key (metadata only — no private key).
     *
     * @return Optional SigningKey with status ACTIVE
     */
    Optional<SigningKey> findActiveKey();

    /**
     * Returns all public keys suitable for token validation (ACTIVE + recently RETIRED).
     *
     * @return list of SigningKeys for JWKS endpoint
     */
    List<SigningKey> findPublicKeys();

    /**
     * Persists a new RSA key pair. The adapter encrypts the private key with AES-256-GCM using the
     * provided secret before writing to the database.
     *
     * @param kid the key ID (UUID string)
     * @param keyPair the RSA key pair (public + private)
     * @param notBefore the activation instant
     * @param encryptionSecret the AES encryption secret (from {@code
     *     acs.signing-key.encryption-secret})
     * @return the persisted SigningKey domain model (public metadata only)
     */
    SigningKey saveKeyPair(String kid, KeyPair keyPair, Instant notBefore, String encryptionSecret);

    /**
     * Decrypts and returns the active key pair from storage. Returns empty if no active key exists.
     *
     * @param encryptionSecret the AES decryption secret
     * @return Optional KeyPair if an active key with decryptable private key exists
     */
    Optional<KeyPair> findActiveKeyPair(String encryptionSecret);

    /**
     * Marks the given signing key as RETIRED. Used during key rotation.
     *
     * @param id the signing key UUID
     */
    void markRetired(UUID id);
}
