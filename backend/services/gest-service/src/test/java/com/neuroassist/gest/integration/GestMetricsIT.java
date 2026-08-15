package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GestMetricsIT extends BaseIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void actuatorMetricsEndpointReturns200() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/metrics", String.class);

        assertThat(response.getStatusCode())
                .describedAs("GET /actuator/metrics must return HTTP 200")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void actuatorMetricsBodyContainsNames() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/metrics", String.class);

        assertThat(response.getBody())
                .describedAs("Metrics response must list metric names")
                .contains("names");
    }

    @Test
    void jvmMemoryUsedMetricIsExposed() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/metrics/jvm.memory.used", String.class);

        assertThat(response.getStatusCode())
                .describedAs("GET /actuator/metrics/jvm.memory.used must return HTTP 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .describedAs("jvm.memory.used metric must include a measurement value")
                .contains("measurements");
    }

    @Test
    void jvmThreadsLiveMetricIsExposed() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/metrics/jvm.threads.live", String.class);

        assertThat(response.getStatusCode())
                .describedAs("GET /actuator/metrics/jvm.threads.live must return HTTP 200")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void httpServerRequestsMetricIsExposedAfterRequest() {
        // Trigger at least one HTTP request to populate the metric.
        restTemplate.getForEntity("/actuator/health", String.class);

        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/metrics/http.server.requests", String.class);

        assertThat(response.getStatusCode())
                .describedAs(
                        "GET /actuator/metrics/http.server.requests must return HTTP 200 after at least one request")
                .isEqualTo(HttpStatus.OK);
    }
}
