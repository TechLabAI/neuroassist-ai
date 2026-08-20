package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.api.dto.JwkSetResponse;
import com.neuroassist.acs.application.port.out.SigningKeyPort;
import com.neuroassist.acs.domain.model.SigningKey;
import com.neuroassist.acs.domain.model.SigningKeyStatus;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for JwkSetService (ACS-019). Verifies: public key returned, no private key exposure,
 * RFC 7517 fields present.
 */
class JwkSetServiceTest {

    private SigningKeyPort signingKeyPort;
    private JwkSetService service;

    @BeforeEach
    void setUp() {
        signingKeyPort = mock(SigningKeyPort.class);
        service = new JwkSetService(signingKeyPort);
    }

    @Test
    void getPublicKeys_returnsRfc7517JwkSet() throws Exception {
        SigningKey key = buildPublicKey();
        when(signingKeyPort.getPublicKeys()).thenReturn(List.of(key));

        JwkSetResponse response = service.getPublicKeys();

        assertThat(response).isNotNull();
        assertThat(response.keys()).hasSize(1);

        Map<String, Object> jwk = response.keys().get(0);
        assertThat(jwk).containsKey("kty");
        assertThat(jwk).containsKey("kid");
        assertThat(jwk.get("kty")).isEqualTo("RSA");
        assertThat(jwk.get("kid")).isEqualTo(key.kid());
    }

    @Test
    void getPublicKeys_noPrivateKeyInResponse() throws Exception {
        SigningKey key = buildPublicKey();
        when(signingKeyPort.getPublicKeys()).thenReturn(List.of(key));

        JwkSetResponse response = service.getPublicKeys();

        Map<String, Object> jwk = response.keys().get(0);
        // Private exponent must NEVER appear
        assertThat(jwk).doesNotContainKey("d");
        assertThat(jwk).doesNotContainKey("p");
        assertThat(jwk).doesNotContainKey("q");
        assertThat(jwk).doesNotContainKey("dp");
        assertThat(jwk).doesNotContainKey("dq");
        assertThat(jwk).doesNotContainKey("qi");
    }

    @Test
    void getPublicKeys_emptyKeys_returnsEmptyList() {
        when(signingKeyPort.getPublicKeys()).thenReturn(List.of());

        JwkSetResponse response = service.getPublicKeys();

        assertThat(response.keys()).isEmpty();
    }

    @Test
    void getPublicKeys_kidPresent() throws Exception {
        SigningKey key = buildPublicKey();
        when(signingKeyPort.getPublicKeys()).thenReturn(List.of(key));

        JwkSetResponse response = service.getPublicKeys();
        Map<String, Object> jwk = response.keys().get(0);

        assertThat(jwk.get("kid")).isNotNull();
        assertThat(jwk.get("kid").toString()).isNotBlank();
    }

    private SigningKey buildPublicKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(1024); // small key for test speed
        KeyPair pair = gen.generateKeyPair();
        String kid = UUID.randomUUID().toString();

        RSAKey rsaKey =
                new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                        .privateKey((RSAPrivateKey) pair.getPrivate())
                        .keyID(kid)
                        .build();

        return new SigningKey(
                null,
                kid,
                "RS256",
                rsaKey.toPublicJWK().toJSONString(),
                SigningKeyStatus.ACTIVE,
                Instant.now(),
                null);
    }
}
