package com.neuroassist.gest.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P5 security integration tests (GEST-017, GEST-018, INT-GEST-002).
 *
 * <p>Validates four scenarios: (1) unauthenticated → 401, (2) wrong scope → 403, (3) valid JWT with
 * correct scope + tenant claim → 200, (4) valid JWT response contains X-Correlation-Id header.
 *
 * <p>Uses an embedded RSA keypair (generated at class init) so no external Authorization Server is
 * required. The {@link TestJwtDecoderConfig} provides a {@link JwtDecoder} backed by the RSA public
 * key — it is auto-imported by Spring Boot test infrastructure and, because of
 * {@code @ConditionalOnMissingBean(JwtDecoder.class)}, suppresses the auto-configured JWK-URI
 * decoder.
 *
 * <p>The tenant in valid-JWT scenarios is {@code "emp_test"} which maps to schema {@code
 * t_emp_test} created by {@code init-schema.sql}.
 */
@Tag("integration")
@AutoConfigureMockMvc
class SecurityIT extends BaseIntegrationTest {

    // ── RSA keypair — generated once at class-loading time ────────────────────
    private static final KeyPair KEY_PAIR;

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KEY_PAIR = gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // ── TestConfiguration — provides @Primary JwtDecoder backed by RSA key ───
    @TestConfiguration
    static class TestJwtDecoderConfig {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            RSAPublicKey publicKey = (RSAPublicKey) KEY_PAIR.getPublic();
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        }
    }

    private MockMvc mockMvc;

    @Autowired
    void setMockMvc(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // ── Scenario 1: unauthenticated request → 401 ────────────────────────────

    @Test
    void unauthenticated_request_returns_401() throws Exception {
        mockMvc.perform(get("/gest-service/tipo-gesto")).andExpect(status().isUnauthorized());
    }

    // ── Scenario 2: valid JWT but wrong scope → 403 ──────────────────────────

    @Test
    void request_with_wrong_scope_returns_403() throws Exception {
        String token = buildToken("user-001", "emp_test", "gest.admin");

        mockMvc.perform(get("/gest-service/tipo-gesto").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── Scenario 3: valid JWT + correct scope + tenant claim → 200 ───────────

    @Test
    void request_with_valid_scope_and_tenant_returns_200() throws Exception {
        String token = buildToken("user-001", "emp_test", "gest.tipo-gesto.read");

        mockMvc.perform(get("/gest-service/tipo-gesto").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ── Scenario 4: any valid JWT response contains X-Correlation-Id ─────────

    @Test
    void response_with_valid_jwt_contains_correlation_id_header() throws Exception {
        String token = buildToken("user-001", "emp_test", "gest.tipo-gesto.read");

        mockMvc.perform(get("/gest-service/tipo-gesto").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    // ── Scenario 5: POST /detection-contract with read-only scope → 403 ──────
    //
    // E2E-026: the POST endpoint must be guarded by gest.detection-contract.validar,
    // not gest.detection-contract.read. A caller with only the read authority must be
    // rejected — before this fix both GET and POST shared the same read guard, so a
    // role that had .read could write without realising it.

    @Test
    void post_detection_contract_with_read_only_scope_returns_403() throws Exception {
        // Token carries only the read authority — deliberately excluded validar
        String token = buildToken("user-001", "emp_test", "gest.detection-contract.read");

        mockMvc.perform(
                        post("/gest-service/detection-contract")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(
                                        "{\"report\":{\"gestureId\":\"g1\","
                                                + "\"confidence\":0.91,\"windowMs\":700}}"))
                .andExpect(status().isForbidden());
    }

    // ── Scenario 6: POST /detection-contract with validar scope → 200 ────────

    @Test
    void post_detection_contract_with_validar_scope_returns_200() throws Exception {
        // Token carries the correct write authority for the POST endpoint
        String token = buildToken("user-001", "emp_test", "gest.detection-contract.validar");

        mockMvc.perform(
                        post("/gest-service/detection-contract")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(
                                        "{\"report\":{\"gestureId\":\"g1\","
                                                + "\"confidence\":0.91,\"windowMs\":700}}"))
                .andExpect(status().isOk());
    }

    // ── JWT helper ────────────────────────────────────────────────────────────

    /**
     * Builds a signed JWT with the embedded RSA private key. The {@code scope} claim carries the
     * space-separated authority strings, which are mapped directly (no {@code SCOPE_} prefix) by
     * the {@link JwtGrantedAuthoritiesConverter} configured in {@link
     * com.neuroassist.gest.infrastructure.config.SecurityConfig}.
     */
    private static String buildToken(String subject, String tenant, String... scopes)
            throws Exception {
        RSAPrivateKey privateKey = (RSAPrivateKey) KEY_PAIR.getPrivate();
        JWSSigner signer = new RSASSASigner(privateKey);

        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject(subject)
                        .issueTime(new Date())
                        .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                        // E2E-021 tornou "tenant_id" o claim canonico e unico; este teste
                        // continuou emitindo o alias "tenant". O TenantContextFilter nao o
                        // encontrava, caia no "sub" ("user-001"), procurava o schema "t_user-001",
                        // nao achava e devolvia 503 — inclusive nos cenarios que so queriam provar
                        // 401/403.
                        .claim("tenant_id", tenant)
                        .claim("scope", String.join(" ", scopes))
                        .build();

        SignedJWT signedJWT =
                new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }
}
