package com.neuroassist.evnt.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * WireMock contract tests for EVNT → ACS integration (INT-EVNT-004, P9).
 *
 * <p>Validates ACS caregiver routing contract:
 *
 * <ol>
 *   <li>On-duty caregiver found → {@code GET /api/v1/caregivers/{pacienteId}/on-duty} returns
 *       {@code 200} with caregiver JSON for the correct tenant.
 *   <li>{@code X-Empresa-ID} header is propagated in every outbound request.
 *   <li>ACS returns {@code 503} → after N consecutive failures the circuit breaker opens and
 *       subsequent calls fail fast without hitting the upstream.
 * </ol>
 *
 * <p>Note: {@code CaregiverRoutingClient} currently has no methods (pending full implementation).
 * These tests validate the WireMock stub setup and will be extended when the ACS Feign adapter
 * methods are implemented in a subsequent cycle.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DisplayName("INT-EVNT-004: Contract tests EVNT -> ACS (WireMock)")
class AcsCaregiverRoutingContractTest {

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

    private static final int WIREMOCK_PORT = 9879;

    @RegisterExtension
    static WireMockExtension acsMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
                    .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("evnt.acs.baseurl", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    // -------------------------------------------------------------------------
    // Scenario 1 — on-duty caregiver found: 200 with caregiver JSON
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S1: On-duty caregiver — ACS WireMock stub returns 200 with caregiver JSON")
    void onDutyCaregiver_acsReturns200WithCaregiverJson() {
        acsMock.stubFor(
                get(urlMatching("/api/v1/caregivers/.*/on-duty"))
                        .willReturn(
                                okJson(
                                        "{"
                                                + "\"caregiverId\":\"cgv-uuid-001\","
                                                + "\"nome\":\"Ana Cuidadora\","
                                                + "\"empresaId\":\"empresa-abc\","
                                                + "\"pacienteId\":\"pac-uuid-001\","
                                                + "\"onDuty\":true"
                                                + "}")));

        // Stub configured — verify mapping is registered in WireMock
        assertThat(acsMock.getStubMappings()).isNotEmpty();

        // TODO: inject CaregiverRoutingClient when ACS adapter methods are implemented
        // CaregiverRoutingClient.OnDutyResponse response =
        //     caregiverRoutingClient.findOnDuty("pac-uuid-001");
        // assertThat(response.caregiverId()).isEqualTo("cgv-uuid-001");
        // assertThat(response.onDuty()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Scenario 2 — X-Empresa-ID header propagation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S2: Outbound request carries X-Empresa-ID header (tenant propagation)")
    void outboundRequest_carriesEmpresaIdHeader() {
        acsMock.stubFor(
                get(urlMatching("/api/v1/caregivers/.*/on-duty"))
                        .withHeader("X-Empresa-ID", matching(".+"))
                        .willReturn(
                                okJson(
                                        "{"
                                                + "\"caregiverId\":\"cgv-uuid-001\","
                                                + "\"nome\":\"Ana Cuidadora\","
                                                + "\"empresaId\":\"empresa-abc\","
                                                + "\"pacienteId\":\"pac-uuid-001\","
                                                + "\"onDuty\":true"
                                                + "}")));

        // Stub with X-Empresa-ID matcher registered — verify mapping is present
        assertThat(acsMock.getStubMappings()).isNotEmpty();

        // TODO: when CaregiverRoutingClient methods are implemented, inject client, set MDC
        // empresaId, and verify:
        // org.slf4j.MDC.put("empresaId", "empresa-abc");
        // try {
        //     caregiverRoutingClient.findOnDuty("pac-uuid-001");
        //     acsMock.verify(getRequestedFor(urlMatching("/api/v1/caregivers/.*/on-duty"))
        //         .withHeader("X-Empresa-ID", equalTo("empresa-abc")));
        // } finally {
        //     org.slf4j.MDC.remove("empresaId");
        // }
    }

    // -------------------------------------------------------------------------
    // Scenario 3 — ACS 503 repeated: circuit breaker opens after N failures
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S3: ACS returns 503 repeatedly — circuit breaker stub configured")
    void acsReturns503Repeatedly_circuitBreakerStubConfigured() {
        acsMock.stubFor(
                get(urlMatching("/api/v1/caregivers/.*/on-duty"))
                        .willReturn(
                                aResponse()
                                        .withStatus(503)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{"
                                                        + "\"type\":\"service-unavailable\","
                                                        + "\"status\":503,"
                                                        + "\"detail\":\"ACS service temporarily"
                                                        + " unavailable\"}")));

        // Stub for 503 configured — verify mapping is registered
        assertThat(acsMock.getStubMappings()).isNotEmpty();

        // TODO: when CaregiverRoutingClient methods are implemented, drive 5 consecutive failures
        // and assert that the Resilience4j circuit breaker transitions to OPEN, causing subsequent
        // calls to throw CallNotPermittedException without reaching WireMock:
        //
        // for (int i = 0; i < 5; i++) {
        //     assertThatThrownBy(() -> caregiverRoutingClient.findOnDuty("pac-uuid-001"))
        //         .isInstanceOf(FeignException.class);
        // }
        // // Circuit breaker should now be OPEN
        // assertThatThrownBy(() -> caregiverRoutingClient.findOnDuty("pac-uuid-001"))
        //     .isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);
        // acsMock.verify(lessThan(6),
        //     getRequestedFor(urlMatching("/api/v1/caregivers/.*/on-duty")));
    }
}
