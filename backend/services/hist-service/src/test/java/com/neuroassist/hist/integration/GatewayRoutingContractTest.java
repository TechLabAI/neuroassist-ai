package com.neuroassist.hist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * INT-HIST-005 — Gateway routing contract test (P5).
 *
 * <p>Smoke-level verification that all canonical {@code /api/v1/hist/**} endpoints are reachable
 * (i.e. mapped to a controller). A request without an Authorization token must return HTTP 401,
 * <em>not</em> 404 — the distinction confirms the route exists in Spring Security's filter chain.
 *
 * <p>Actuator health check must return 200 unconditionally (no token required).
 *
 * <p>Does NOT test business logic — only that the controller mapping wiring is correct. Requires:
 * Testcontainers PostgreSQL (inherited from {@link BaseIntegrationTest}).
 */
class GatewayRoutingContractTest extends BaseIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("POST /api/v1/hist/audit-facts without token → 401 (endpoint mapped)")
    void postAuditFacts_withoutToken_returns401() {
        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/v1/hist/audit-facts", "{}", String.class);

        assertThat(response.getStatusCode())
                .as("Endpoint must exist — 401 expected, 404 means route is missing")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /api/v1/hist/history without token → 401 (endpoint mapped)")
    void getHistory_withoutToken_returns401() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/hist/history", String.class);

        assertThat(response.getStatusCode())
                .as("Endpoint must exist — 401 expected, 404 means route is missing")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /api/v1/hist/history/verify without token → 401 (endpoint mapped)")
    void getHistoryVerify_withoutToken_returns401() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        "/api/v1/hist/history/verify?streamKey=test&tenantId=t1", String.class);

        assertThat(response.getStatusCode())
                .as("Endpoint must exist — 401 expected, 404 means route is missing")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /api/v1/hist/audit-records/{id} without token → 401 (endpoint mapped)")
    void getAuditRecord_withoutToken_returns401() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        "/api/v1/hist/audit-records/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode())
                .as("Endpoint must exist — 401 expected, 404 means route is missing")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /api/v1/hist/audit-facts/{id} without token → 401 (endpoint mapped)")
    void getAuditFact_withoutToken_returns401() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        "/api/v1/hist/audit-facts/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode())
                .as("Endpoint must exist — 401 expected, 404 means route is missing")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /actuator/health → 200 (public endpoint, no token needed)")
    void getActuatorHealth_noToken_returns200() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode())
                .as("Actuator health must be public (no token required)")
                .isEqualTo(HttpStatus.OK);
    }
}
