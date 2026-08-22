package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.application.port.out.SigningKeyPort;
import com.neuroassist.acs.domain.model.SigningKey;
import com.neuroassist.acs.domain.model.SigningKeyStatus;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Claim-level verification of the device token ({@code standards/service-identity.md} §7.3) —
 * signed with a real RSA key and decoded back, so the assertions are on the wire format rather than
 * on a builder call.
 */
class TokenIssuerDeviceTokenTest {

    private static final String CLIENT_ID = "edge-3fd6f0a2-0000-4000-8000-000000000001";
    private static final UUID TENANT_ID = UUID.randomUUID();

    private RSAKey rsaKey;
    private TokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("kid-test").generate();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));

        SigningKeyPort signingKeyPort = mock(SigningKeyPort.class);
        when(signingKeyPort.getActiveSigningKey())
                .thenReturn(
                        new SigningKey(
                                UUID.randomUUID(),
                                "kid-test",
                                "RS256",
                                rsaKey.toPublicJWK().toJSONString(),
                                SigningKeyStatus.ACTIVE,
                                Instant.now().minusSeconds(60),
                                null));

        tokenIssuer = new TokenIssuer(encoder, signingKeyPort, mock(RoleAuthorityPolicy.class));
        ReflectionTestUtils.setField(tokenIssuer, "expirySeconds", 900L);
        ReflectionTestUtils.setField(tokenIssuer, "refreshExpirySeconds", 604800L);
        ReflectionTestUtils.setField(tokenIssuer, "issuer", "acs-service");
    }

    @Test
    @DisplayName("device token carries sub=clientId, token_type=device and edge.device")
    void deviceTokenClaims() throws Exception {
        AutenticacaoResponse response =
                tokenIssuer.issueDeviceToken(CLIENT_ID, TENANT_ID, List.of("edge.device"));

        Jwt jwt = decode(response.accessToken());

        assertThat(jwt.getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("device");
        assertThat(jwt.getClaimAsStringList("authorities")).containsExactly("edge.device");
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(TENANT_ID.toString());
        assertThat(jwt.getClaimAsString("tenant")).isEqualTo(TENANT_ID.toString());
        assertThat(jwt.getClaimAsString("empresaId")).isEqualTo(TENANT_ID.toString());
        assertThat(jwt.getClaimAsString("papel")).isNull();
    }

    @Test
    @DisplayName("device token has no refresh token — the device re-authenticates with its secret")
    void noRefreshToken() {
        AutenticacaoResponse response =
                tokenIssuer.issueDeviceToken(CLIENT_ID, TENANT_ID, List.of("edge.device"));

        assertThat(response.refreshToken()).isNull();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.tenant()).isEqualTo(TENANT_ID);
        assertThat(response.papel()).isNull();
    }

    @Test
    @DisplayName("a tenant-less service token simply omits the tenant claims — never a placeholder")
    void tenantlessServiceTokenOmitsTenantClaims() throws Exception {
        AutenticacaoResponse response =
                tokenIssuer.issueServiceToken("acs-service", null, List.of("edge.internal"));

        Jwt jwt = decode(response.accessToken());

        assertThat(jwt.getSubject()).isEqualTo("acs-service");
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("service");
        assertThat(jwt.getClaimAsStringList("authorities")).containsExactly("edge.internal");
        assertThat(jwt.hasClaim("tenant_id")).isFalse();
        assertThat(jwt.hasClaim("tenant")).isFalse();
        assertThat(jwt.hasClaim("empresaId")).isFalse();
        assertThat(response.tenant()).isNull();
    }

    private Jwt decode(String token) throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.toPublicKey();
        return NimbusJwtDecoder.withPublicKey(publicKey).build().decode(token);
    }
}
