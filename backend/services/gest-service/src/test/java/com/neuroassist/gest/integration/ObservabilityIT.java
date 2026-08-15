package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Observability integration tests for gest-service (GEST-021 — P7).
 *
 * <p>Verifies that:
 *
 * <ol>
 *   <li>{@code /actuator/health} returns HTTP 200 without an {@code Authorization} header
 *       (permitAll in SecurityConfig).
 *   <li>{@code /actuator/prometheus} exposes {@code gest_profile_versions_published_total}.
 *   <li>{@code /actuator/prometheus} exposes {@code gest_tipo_gesto_registrations_total} and {@code
 *       gest_tipo_gesto_transitions_total}.
 * </ol>
 *
 * <p>Uses {@link TestRestTemplate} (no WebFlux dependency). Extends {@link BaseIntegrationTest} for
 * the Testcontainers PostgreSQL singleton and {@code @ActiveProfiles("test")}.
 *
 * <p>Note: The Prometheus endpoint is accessible without authentication because {@code
 * /actuator/**} is currently exposed via {@code permitAll()} for {@code health} and {@code info},
 * while prometheus is protected only by network/ops-level controls in production (MVP). If
 * SecurityConfig changes to restrict prometheus, update the request here to include a valid JWT or
 * use {@code @WithMockUser}.
 */
class ObservabilityIT extends BaseIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    /**
     * Scenario 1: /actuator/health returns 200 without JWT.
     *
     * <p>The SecurityConfig in production has {@code /actuator/health} in {@code permitAll()}, so
     * this endpoint must respond even without an {@code Authorization} header.
     */
    @Test
    void actuator_health_returns200_withoutJwt() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    /**
     * Scenario 2: /actuator/prometheus exposes gest_profile_versions_published_total.
     *
     * <p>This metric is registered on first access (lazy registration via Micrometer's counter
     * factory). Since the counter may not have been incremented yet, we verify only that the metric
     * name appears in the prometheus output (registered but count=0 is sufficient for the gate).
     */
    @Test
    void actuator_prometheus_exposes_gestProfileVersionsPublishedTotal() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Metric name is prefixed by Micrometer (underscores, _total suffix for counters)
        assertThat(response.getBody())
                .as("/actuator/prometheus must mention gest_profile_versions_published_total")
                .contains("gest_profile_versions_published");
    }

    /**
     * Scenario 3: /actuator/prometheus exposes tipo-gesto domain metrics.
     *
     * <p>Verifies that the two TipoGesto counters (registrations + transitions) are present in the
     * prometheus scrape output. Counters may be at 0 — presence is what matters here.
     */
    @Test
    void actuator_prometheus_exposes_gestTipoGestoMetrics() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("/actuator/prometheus must mention gest_tipo_gesto_registrations_total")
                .contains("gest_tipo_gesto_registrations");
        assertThat(response.getBody())
                .as("/actuator/prometheus must mention gest_tipo_gesto_transitions_total")
                .contains("gest_tipo_gesto_transitions");
    }
}
