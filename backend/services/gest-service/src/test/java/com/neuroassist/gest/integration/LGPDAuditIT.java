package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Integration test — LGPD / biometric data audit (Soft score §6).
 *
 * <p>NeuroAssist AI processes biometric signals (facial landmarks, timing windows, confidence
 * scores). LGPD Art. 11 requires that biometric data is handled with strict purpose limitation and
 * that error responses do not inadvertently expose raw biometric fields in their payload.
 *
 * <p>These tests verify that error responses from the GEST service do not leak sensitive biometric
 * field names: {@code video}, {@code image}, {@code landmark}, {@code confidence}, {@code
 * timingWindow}. A proper RFC 7807 ProblemDetail error body should contain only metadata (type,
 * status, title, detail, errors), never raw signal data.
 *
 * <p><b>Por que Bearer JWT e não HTTP Basic.</b> As requisições carregavam Basic do usuário em
 * memória {@code gest-manager}, que autentica mas não traz claim de tenant nenhum. Sem tenant o
 * {@code TenantIdentifierResolver} cai no fallback {@code neuroassist_global}, onde as tabelas do
 * GEST não existem — e desde que as migrations reais passaram a desenhar o schema (E2E-022) a busca
 * por um id inexistente morria com {@code relation "tipo_gesto" does not exist} e virava
 * <b>500</b>, nunca o 404 que este teste existe para auditar. O teste media o corpo de erro errado:
 * auditava um erro de infraestrutura achando que auditava o not-found de domínio.
 *
 * <p>O JWT assinado pela chave embutida reproduz o caminho de produção — claim {@code tenant_id} (o
 * canônico desde E2E-021), tenant {@code emp_test} roteando para o schema {@code t_emp_test} que
 * {@code SchemaMigrations} provisiona. Aí sim o 404 é o 404 do controller.
 */
class LGPDAuditIT extends BaseIntegrationTest {

    /** Fields whose presence in an error body would indicate a biometric data leak. */
    private static final String[] BIOMETRIC_FIELDS = {
        "video", "image", "landmark", "confidence", "timingWindow"
    };

    /**
     * Tenant provisionado por {@code SchemaMigrations} — roteia para o schema {@code t_emp_test}.
     */
    private static final String TEST_TENANT = "emp_test";

    /** Par de chaves RSA gerado uma vez por JVM — assina os tokens que o decoder abaixo aceita. */
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

    /** Substitui o decoder por JWK-URI (que exigiria um Authorization Server de verdade). */
    @TestConfiguration
    static class LgpdJwtDecoderConfig {

        @Bean
        @Primary
        JwtDecoder lgpdTestJwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic()).build();
        }
    }

    @Autowired private TestRestTemplate restTemplate;

    /**
     * Token de gerente: as authorities são as mesmas que o {@code RoleAuthorityPolicy} do ACS emite
     * para COORDENADOR_CLINICA e que os {@code @PreAuthorize} do controller exigem, verbatim.
     */
    private static String managerBearer() {
        try {
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .subject("lgpd-audit-user")
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                            .claim("tenant_id", TEST_TENANT)
                            .claim(
                                    "scope",
                                    "gest.tipo-gesto.read gest.tipo-gesto.criar"
                                            + " gest.tipo-gesto.editar")
                            .build();
            JWSSigner signer = new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate());
            SignedJWT jwt =
                    new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
            jwt.sign(signer);
            return "Bearer " + jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar o token do teste LGPD", e);
        }
    }

    /** JSON request headers plus the manager Bearer token. */
    private static HttpHeaders jsonAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, managerBearer());
        return headers;
    }

    /** Headers carrying only the manager Bearer token (for GET requests). */
    private static HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, managerBearer());
        return headers;
    }

    @Test
    void validationErrorDoesNotLeakBiometricFields() {
        // POST with blank nome → 400 validation error
        String body = "{\"nome\":\"\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, jsonAuthHeaders());

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/gest-service/tipo-gesto", HttpMethod.POST, entity, String.class);

        assertThat(response.getStatusCode().value())
                .describedAs("Blank nome must produce a 4xx error")
                .isBetween(400, 499);

        String responseBody = response.getBody();
        for (String field : BIOMETRIC_FIELDS) {
            assertThat(responseBody)
                    .describedAs("LGPD: error response must not expose biometric field '%s'", field)
                    .doesNotContainIgnoringCase(field);
        }
    }

    @Test
    void notFoundErrorDoesNotLeakBiometricFields() {
        // GET a non-existent UUID → 404 not-found error
        String nonExistentId = "00000000-0000-0000-0000-000000000001";
        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/gest-service/tipo-gesto/" + nonExistentId,
                        HttpMethod.GET,
                        new HttpEntity<>(authHeaders()),
                        String.class);

        // 404 exato, e nao "algum 4xx": o 500 que esta rota devolvia antes ja passava por 4xx
        // nenhum, mas um 403 de tenant faltando passaria pela faixa e o teste voltaria a auditar
        // um corpo que nao e o do not-found.
        assertThat(response.getStatusCode().value())
                .describedAs("Non-existent ID must produce a 404")
                .isEqualTo(404);

        String responseBody = response.getBody();
        for (String field : BIOMETRIC_FIELDS) {
            assertThat(responseBody)
                    .describedAs(
                            "LGPD: not-found error response must not expose biometric field '%s'",
                            field)
                    .doesNotContainIgnoringCase(field);
        }
    }

    @Test
    void nomeTooLongErrorDoesNotLeakBiometricFields() {
        // POST with 121-character nome → 400 validation error
        String longNome = "B".repeat(121);
        String body = "{\"nome\":\"" + longNome + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, jsonAuthHeaders());

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/gest-service/tipo-gesto", HttpMethod.POST, entity, String.class);

        assertThat(response.getStatusCode().value())
                .describedAs("Over-length nome must produce a 4xx error")
                .isBetween(400, 499);

        String responseBody = response.getBody();
        for (String field : BIOMETRIC_FIELDS) {
            assertThat(responseBody)
                    .describedAs(
                            "LGPD: validation error response must not expose biometric field '%s'",
                            field)
                    .doesNotContainIgnoringCase(field);
        }
    }

    @Test
    void successResponseDoesNotLeakBiometricFields() {
        // POST a valid creation → 201 — success body must also be clean.
        String body = "{\"nome\":\"lgpd-test-gesto\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, jsonAuthHeaders());

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/gest-service/tipo-gesto", HttpMethod.POST, entity, String.class);

        // Sem esta linha o teste era uma guarda que nao guardava nada: um 403 ou um 500 traziam
        // corpo sem campo biometrico algum e ele passava sem nunca ter criado um TipoGesto.
        assertThat(response.getStatusCode().value())
                .describedAs("Valid creation must return 201")
                .isEqualTo(201);

        String responseBody = response.getBody();
        if (responseBody != null) {
            for (String field : BIOMETRIC_FIELDS) {
                assertThat(responseBody)
                        .describedAs(
                                "LGPD: success response must not expose biometric field '%s'",
                                field)
                        .doesNotContainIgnoringCase(field);
            }
        }
    }
}
