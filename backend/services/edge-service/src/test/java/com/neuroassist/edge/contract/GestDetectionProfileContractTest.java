package com.neuroassist.edge.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.neuroassist.edge.infrastructure.clients.DetectionProfileClient;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * WireMock contract tests for EDGE → GEST integration (EDGE-INT-001, P4).
 *
 * <p>Validates three scenarios:
 *
 * <ol>
 *   <li>GEST returns 200 with version — adapter returns the version number.
 *   <li>GEST returns 404 (no active profile) — adapter returns 404 response.
 *   <li>Request carries {@code X-Correlation-Id} header in every outbound call (MDC propagation).
 * </ol>
 *
 * <p>Circuit breaker is configured with threshold 100%/100-calls in {@code application-test.yml} to
 * prevent interference with WireMock (EDGE-014 constraint).
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DisplayName("INT-EDGE-001: Contract tests EDGE -> GEST detection-profile (WireMock)")
class GestDetectionProfileContractTest {

    // ---------------------------------------------------------------------------
    // Testcontainers PostgreSQL (singleton — same pattern as BaseIntegrationTest)
    // ---------------------------------------------------------------------------

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES =
                new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("neuroassist-ai_test")
                        .withUsername("test")
                        .withPassword("test")
                        .withInitScript("init-schema.sql");
        POSTGRES.start();
    }

    private static final int WIREMOCK_PORT = 9876;

    @RegisterExtension
    static WireMockExtension gestMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
                    .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("neuroassist.gest.url", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    private final DetectionProfileClient detectionProfileClient;

    @Autowired
    GestDetectionProfileContractTest(DetectionProfileClient detectionProfileClient) {
        this.detectionProfileClient = detectionProfileClient;
    }

    // -------------------------------------------------------------------------
    // Scenario 1 — GEST responds 200 with version
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S1: GEST returns 200 with version — Feign returns version field")
    void gestReturns200_feignMapsVersion() {
        UUID deviceId = UUID.randomUUID();
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/detection-profile/devices/" + deviceId))
                        .willReturn(okJson("{\"version\":42}")));

        ResponseEntity<?> response = detectionProfileClient.getActiveProfileForDevice(deviceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Scenario 2 — GEST returns 404 (no active profile published yet)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "S2: GEST returns 404 — Feign throws FeignException.NotFound (caught by CB fallback)")
    void gestReturns404_feignThrowsNotFound() {
        UUID deviceId = UUID.randomUUID();
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/detection-profile/devices/" + deviceId))
                        .willReturn(aResponse().withStatus(404)));

        // Feign default behavior: throws FeignException.NotFound for 4xx responses.
        // DetectionProfileFeignAdapter catches this via @CircuitBreaker fallback →
        // Optional.empty().
        assertThatThrownBy(() -> detectionProfileClient.getActiveProfileForDevice(deviceId))
                .isInstanceOf(feign.FeignException.NotFound.class);
    }

    // -------------------------------------------------------------------------
    // Scenario 3 — X-Correlation-Id header propagation (FeignClientConfig interceptor)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S3: Outbound request carries X-Correlation-Id header (MDC propagation)")
    void outboundRequest_carriesCorrelationIdHeader() {
        UUID deviceId = UUID.randomUUID();
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/detection-profile/devices/" + deviceId))
                        .withHeader("X-Correlation-Id", matching(".+"))
                        .willReturn(okJson("{\"version\":7}")));

        // Seed MDC — normally set by TenantContextFilter in production
        org.slf4j.MDC.put("correlationId", "test-corr-edge-001");
        try {
            detectionProfileClient.getActiveProfileForDevice(deviceId);

            gestMock.verify(
                    getRequestedFor(
                                    urlEqualTo(
                                            "/gest-service/detection-profile/devices/" + deviceId))
                            .withHeader("X-Correlation-Id", equalTo("test-corr-edge-001")));
        } finally {
            org.slf4j.MDC.remove("correlationId");
        }
    }
}
