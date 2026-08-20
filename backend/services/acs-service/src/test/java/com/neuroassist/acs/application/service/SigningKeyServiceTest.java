package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.application.port.out.SigningKeyRepositoryPort;
import com.neuroassist.acs.domain.model.SigningKey;
import com.neuroassist.acs.domain.model.SigningKeyStatus;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for SigningKeyService (ACS-026, Item A5). All repository calls are mocked — no Spring
 * context or database required.
 */
class SigningKeyServiceTest {

    private SigningKeyRepositoryPort signingKeyRepository;
    private SigningKeyService service;

    private static final String ENCRYPTION_SECRET = "test-secret-for-aes-256-gcm";
    private static final String KID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        signingKeyRepository = mock(SigningKeyRepositoryPort.class);
        service = new SigningKeyService(signingKeyRepository);
        // Inject @Value field via ReflectionTestUtils (not Spring context)
        ReflectionTestUtils.setField(service, "encryptionSecret", ENCRYPTION_SECRET);
    }

    /**
     * Cenário A5-1: getOrCreateActiveKey() when repository is empty → generates and persists new
     * key.
     */
    @Test
    void getOrCreateActiveKey_noExistingKey_generatesAndPersistsNew() throws Exception {
        when(signingKeyRepository.findActiveKeyPair(ENCRYPTION_SECRET))
                .thenReturn(Optional.empty());
        when(signingKeyRepository.findActiveKey()).thenReturn(Optional.empty());
        when(signingKeyRepository.saveKeyPair(
                        anyString(), any(KeyPair.class), any(Instant.class), eq(ENCRYPTION_SECRET)))
                .thenReturn(buildSigningKeyDomain(KID));

        RSAKey result = service.getOrCreateActiveKey();

        assertThat(result).isNotNull();
        assertThat(result.getKeyID()).isNotNull();
        assertThat(result.toRSAPrivateKey()).isNotNull();
        assertThat(result.toRSAPublicKey()).isNotNull();

        // Verify that a new key was persisted
        verify(signingKeyRepository)
                .saveKeyPair(
                        anyString(), any(KeyPair.class), any(Instant.class), eq(ENCRYPTION_SECRET));
    }

    /**
     * Cenário A5-2: getOrCreateActiveKey() when repository has an existing key → reuses without
     * generating a new one.
     */
    @Test
    void getOrCreateActiveKey_existingKeyPresent_reusesWithoutGeneratingNew() throws Exception {
        KeyPair existingPair = generateRsaKeyPair();
        SigningKey existingMeta = buildSigningKeyDomain(KID);

        when(signingKeyRepository.findActiveKeyPair(ENCRYPTION_SECRET))
                .thenReturn(Optional.of(existingPair));
        when(signingKeyRepository.findActiveKey()).thenReturn(Optional.of(existingMeta));

        RSAKey result = service.getOrCreateActiveKey();

        assertThat(result).isNotNull();
        assertThat(result.getKeyID()).isEqualTo(KID);

        // Verify NO new key was persisted
        verify(signingKeyRepository, never())
                .saveKeyPair(anyString(), any(KeyPair.class), any(Instant.class), anyString());
    }

    /**
     * Cenário A5-3: rotateIfExpired() when active key is older than 7 days → generates new key +
     * marks old as RETIRED.
     */
    @Test
    void rotateIfExpired_keyExpiredOver7Days_rotatesKey() {
        Instant expiredNotBefore = Instant.now().minus(8, ChronoUnit.DAYS);
        UUID oldKeyId = UUID.randomUUID();
        SigningKey expiredKey =
                new SigningKey(
                        oldKeyId,
                        KID,
                        "RS256",
                        "{}",
                        SigningKeyStatus.ACTIVE,
                        expiredNotBefore,
                        null);

        when(signingKeyRepository.findActiveKey()).thenReturn(Optional.of(expiredKey));
        when(signingKeyRepository.saveKeyPair(
                        anyString(), any(KeyPair.class), any(Instant.class), eq(ENCRYPTION_SECRET)))
                .thenReturn(buildSigningKeyDomain(UUID.randomUUID().toString()));

        service.rotateIfExpired();

        // Verify: new key was saved
        verify(signingKeyRepository)
                .saveKeyPair(
                        anyString(), any(KeyPair.class), any(Instant.class), eq(ENCRYPTION_SECRET));
        // Verify: old key was retired
        verify(signingKeyRepository).markRetired(oldKeyId);
    }

    /**
     * Cenário A5-4: rotateIfExpired() when active key is recent (< 7 days) → noop (no new key, no
     * retire).
     */
    @Test
    void rotateIfExpired_keyRecent_doesNotRotate() {
        Instant recentNotBefore = Instant.now().minus(2, ChronoUnit.DAYS);
        UUID keyId = UUID.randomUUID();
        SigningKey recentKey =
                new SigningKey(
                        keyId, KID, "RS256", "{}", SigningKeyStatus.ACTIVE, recentNotBefore, null);

        when(signingKeyRepository.findActiveKey()).thenReturn(Optional.of(recentKey));

        service.rotateIfExpired();

        // Verify: NO new key persisted, NO retire called
        verify(signingKeyRepository, never())
                .saveKeyPair(anyString(), any(KeyPair.class), any(Instant.class), anyString());
        verify(signingKeyRepository, never()).markRetired(any(UUID.class));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SigningKey buildSigningKeyDomain(String kid) {
        return new SigningKey(
                UUID.randomUUID(),
                kid,
                "RS256",
                "{\"kty\":\"RSA\"}",
                SigningKeyStatus.ACTIVE,
                Instant.now(),
                null);
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }
}
