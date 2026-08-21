package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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
 * <p>Requests carry HTTP Basic credentials for the in-memory {@code gest-manager} user: since
 * gest-service became a real resource server ({@code anyRequest().authenticated()} +
 * {@code @EnableMethodSecurity}) an anonymous call is short-circuited with an empty-bodied 401,
 * which would make these assertions inspect a {@code null} body instead of the domain error payload
 * they exist to audit. Basic (rather than a JWT) keeps the tenant unresolved, exactly as before.
 */
class LGPDAuditIT extends BaseIntegrationTest {

    /** Fields whose presence in an error body would indicate a biometric data leak. */
    private static final String[] BIOMETRIC_FIELDS = {
        "video", "image", "landmark", "confidence", "timingWindow"
    };

    private static final String BASIC_MANAGER =
            "Basic "
                    + java.util.Base64.getEncoder()
                            .encodeToString(
                                    "gest-manager:manager-secret"
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Autowired private TestRestTemplate restTemplate;

    /** JSON request headers plus HTTP Basic credentials for the in-memory manager user. */
    private static HttpHeaders jsonAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, BASIC_MANAGER);
        return headers;
    }

    /** Headers carrying only HTTP Basic credentials (for GET requests). */
    private static HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, BASIC_MANAGER);
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

        assertThat(response.getStatusCode().value())
                .describedAs("Non-existent ID must produce a 4xx error")
                .isBetween(400, 499);

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
