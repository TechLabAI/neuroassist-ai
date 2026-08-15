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
 */
class LGPDAuditIT extends BaseIntegrationTest {

    /** Fields whose presence in an error body would indicate a biometric data leak. */
    private static final String[] BIOMETRIC_FIELDS = {
        "video", "image", "landmark", "confidence", "timingWindow"
    };

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void validationErrorDoesNotLeakBiometricFields() {
        // POST with blank nome → 400 validation error
        String body = "{\"nome\":\"\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

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
                restTemplate.getForEntity(
                        "/gest-service/tipo-gesto/" + nonExistentId, String.class);

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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

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
