package com.neuroassist.evnt.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.neuroassist.evnt.infrastructure.clients.GestValidationClient;
import feign.FeignException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * WireMock contract tests for EVNT → GEST integration (INT-EVNT-002, P9).
 *
 * <p>Validates three scenarios:
 *
 * <ol>
 *   <li>Valid report → GEST returns {@code 200 {valid:true}} → adapter returns {@code true}.
 *   <li>GEST rejects unknown primitive → {@code 422} → Feign throws 4xx exception.
 *   <li>Request carries {@code X-Correlation-Id} header in every outbound call.
 * </ol>
 *
 * <p>WireMock stubs are defined programmatically here (JSON mappings in {@code
 * src/test/resources/wiremock/gest-service/} serve as reference fixtures for BDD reuse).
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DisplayName("INT-EVNT-002: Contract tests EVNT -> GEST (WireMock)")
class GestValidationContractTest {

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

    private static final int WIREMOCK_PORT = 9877;

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
        registry.add("evnt.gest.baseurl", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    private GestValidationClient gestValidationClient;

    @Autowired
    void setGestValidationClient(GestValidationClient client) {
        this.gestValidationClient = client;
    }

    // -------------------------------------------------------------------------
    // Scenario 1 — valid report: 200 {valid:true}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S1: Valid primitives — GEST returns 200 valid:true — adapter returns true")
    void validPrimitives_gestReturnsValid() {
        gestMock.stubFor(
                post(urlEqualTo("/api/v1/recognized-commands"))
                        .willReturn(okJson("{\"valid\":true,\"contractVersion\":\"1.0\"}")));

        var request =
                new GestValidationClient.ValidationRequest(
                        1, List.of(new GestValidationClient.PrimitiveRef("P001")));
        var response = gestValidationClient.validate(request);

        assertThat(response.valid()).isTrue();
        assertThat(response.contractVersion()).isEqualTo("1.0");
    }

    // -------------------------------------------------------------------------
    // Scenario 2 — unknown primitive: 422 — Feign throws exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S2: Unknown primitive — GEST returns 422 — Feign throws FeignException")
    void unknownPrimitive_gestReturns422_feignThrows() {
        gestMock.stubFor(
                post(urlEqualTo("/api/v1/recognized-commands"))
                        .willReturn(
                                aResponse()
                                        .withStatus(422)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"valid\":false,\"reason\":\"unknown-primitive\"}")));

        var request =
                new GestValidationClient.ValidationRequest(
                        1, List.of(new GestValidationClient.PrimitiveRef("UNKNOWN_PRIMITIVE")));

        assertThatThrownBy(() -> gestValidationClient.validate(request))
                .isInstanceOf(FeignException.class)
                .satisfies(ex -> assertThat(((FeignException) ex).status()).isEqualTo(422));
    }

    // -------------------------------------------------------------------------
    // Scenario 3 — X-Correlation-Id header propagation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S3: Outbound request carries X-Correlation-Id header (MDC propagation)")
    void outboundRequest_carriesCorrelationIdHeader() {
        gestMock.stubFor(
                post(urlEqualTo("/api/v1/recognized-commands"))
                        .withHeader("X-Correlation-Id", matching(".+"))
                        .willReturn(okJson("{\"valid\":true,\"contractVersion\":\"1.0\"}")));

        // Set MDC correlation id (normally set by TenantContextFilter in production)
        org.slf4j.MDC.put("correlationId", "test-corr-id-001");
        try {
            var request =
                    new GestValidationClient.ValidationRequest(
                            1, List.of(new GestValidationClient.PrimitiveRef("P001")));
            gestValidationClient.validate(request);

            // WireMock received a request matching the header pattern (no exception = success)
            gestMock.verify(
                    postRequestedFor(urlEqualTo("/api/v1/recognized-commands"))
                            .withHeader("X-Correlation-Id", equalTo("test-corr-id-001")));
        } finally {
            org.slf4j.MDC.remove("correlationId");
        }
    }
}
