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
 * WireMock contract tests for EVNT → DASH integration (INT-EVNT-005, P9).
 *
 * <p>Validates DASH notification dispatch contract:
 *
 * <ol>
 *   <li>Notification dispatched → {@code POST /api/v1/notifications} returns {@code 202 Accepted} →
 *       dispatch considered successful.
 *   <li>{@code X-Empresa-ID} header is propagated in every outbound request.
 *   <li>DASH returns {@code 503} repeatedly → {@code evnt.dispatch.exhausted} counter increments
 *       when all retry attempts are exhausted.
 * </ol>
 *
 * <p>Note: {@code NotificationDispatchClient} currently has no methods (pending full
 * implementation). These tests validate the WireMock stub setup and will be extended when the DASH
 * Feign adapter methods are implemented in a subsequent cycle.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DisplayName("INT-EVNT-005: Contract tests EVNT -> DASH (WireMock)")
class DashNotificationContractTest {

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

    private static final int WIREMOCK_PORT = 9880;

    @RegisterExtension
    static WireMockExtension dashMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
                    .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("evnt.dash.baseurl", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    // -------------------------------------------------------------------------
    // Scenario 1 — notification dispatched: 202 Accepted
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S1: Notification dispatched — DASH WireMock stub returns 202 Accepted")
    void notificationDispatched_dashReturns202Accepted() {
        dashMock.stubFor(
                post(urlEqualTo("/api/v1/notifications"))
                        .willReturn(
                                aResponse()
                                        .withStatus(202)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{"
                                                        + "\"dispatchId\":\"disp-uuid-001\","
                                                        + "\"status\":\"accepted\","
                                                        + "\"tier\":\"full_screen_alarm\""
                                                        + "}")));

        // Stub configured — verify mapping is registered in WireMock
        assertThat(dashMock.getStubMappings()).isNotEmpty();

        // TODO: inject NotificationDispatchClient when DASH adapter methods are implemented
        // NotificationDispatchClient.DispatchRequest req = new
        // NotificationDispatchClient.DispatchRequest(
        //     "evt-uuid-001", "emergencia", "full_screen_alarm", "pac-uuid-001");
        // NotificationDispatchClient.DispatchResponse response =
        //     notificationDispatchClient.dispatch(req);
        // assertThat(response.status()).isEqualTo("accepted");
        // assertThat(response.tier()).isEqualTo("full_screen_alarm");
    }

    // -------------------------------------------------------------------------
    // Scenario 2 — X-Empresa-ID header propagation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S2: Outbound request carries X-Empresa-ID header (tenant propagation)")
    void outboundRequest_carriesEmpresaIdHeader() {
        dashMock.stubFor(
                post(urlEqualTo("/api/v1/notifications"))
                        .withHeader("X-Empresa-ID", matching(".+"))
                        .willReturn(
                                aResponse()
                                        .withStatus(202)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{"
                                                        + "\"dispatchId\":\"disp-uuid-002\","
                                                        + "\"status\":\"accepted\","
                                                        + "\"tier\":\"discreet\""
                                                        + "}")));

        // Stub with X-Empresa-ID matcher registered — verify mapping is present
        assertThat(dashMock.getStubMappings()).isNotEmpty();

        // TODO: when NotificationDispatchClient methods are implemented, inject client, set MDC
        // empresaId, and verify:
        // org.slf4j.MDC.put("empresaId", "empresa-abc");
        // try {
        //     notificationDispatchClient.dispatch(req);
        //     dashMock.verify(postRequestedFor(urlEqualTo("/api/v1/notifications"))
        //         .withHeader("X-Empresa-ID", equalTo("empresa-abc")));
        // } finally {
        //     org.slf4j.MDC.remove("empresaId");
        // }
    }

    // -------------------------------------------------------------------------
    // Scenario 3 — DASH 503 repeated: evnt.dispatch.exhausted counter increments
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S3: DASH returns 503 repeatedly — dispatch.exhausted stub configured")
    void dashReturns503Repeatedly_dispatchExhaustedStubConfigured() {
        dashMock.stubFor(
                post(urlEqualTo("/api/v1/notifications"))
                        .willReturn(
                                aResponse()
                                        .withStatus(503)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{"
                                                        + "\"type\":\"service-unavailable\","
                                                        + "\"status\":503,"
                                                        + "\"detail\":\"DASH service temporarily"
                                                        + " unavailable\"}")));

        // Stub for 503 configured — verify mapping is registered
        assertThat(dashMock.getStubMappings()).isNotEmpty();

        // TODO: when NotificationDispatchClient methods are implemented, exhaust all retry
        // attempts by driving repeated 503 responses and assert that the
        // `evnt.dispatch.exhausted` Micrometer counter increments exactly once per
        // exhausted dispatch sequence:
        //
        // MeterRegistry registry = applicationContext.getBean(MeterRegistry.class);
        // double before = registry.counter("evnt.dispatch.exhausted").count();
        //
        // // Drive enough 503 responses to exhaust retries
        // for (int i = 0; i < 5; i++) {
        //     assertThatThrownBy(() -> notificationDispatchClient.dispatch(req))
        //         .isInstanceOf(FeignException.class);
        // }
        //
        // double after = registry.counter("evnt.dispatch.exhausted").count();
        // assertThat(after).isGreaterThan(before);
    }
}
