package com.neuroassist.gest.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.shared.error.CodedException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration test for {@link com.neuroassist.gest.infrastructure.clients.EdgePairingAdapter} via
 * WireMock (P4 — GEST-015, INT-GEST-001). Verifies three scenarios against a real Feign call:
 *
 * <ol>
 *   <li>Device paired → tenantId returned
 *   <li>Device not found (HTTP 404) → CodedException("device-not-paired")
 *   <li>Edge service down (connection fault) → CodedException("edge-unavailable")
 * </ol>
 *
 * <p>Extends {@link BaseIntegrationTest} to reuse the Testcontainers PostgreSQL singleton (required
 * for full Spring context). WireMock is started in a static block before Spring context creation so
 * that {@link #configureWireMock} can supply the base URL via {@code @DynamicPropertySource}.
 */
class EdgePairingClientIT extends BaseIntegrationTest {

    // WireMock server started before Spring context (static initializer)
    static final WireMockServer WIRE_MOCK;

    static {
        WIRE_MOCK = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        WIRE_MOCK.start();
    }

    @DynamicPropertySource
    static void configureWireMock(DynamicPropertyRegistry registry) {
        registry.add("gest.edge.url", WIRE_MOCK::baseUrl);
    }

    private EdgePairingPort edgePairingPort;

    @Autowired
    void setEdgePairingPort(EdgePairingPort edgePairingPort) {
        this.edgePairingPort = edgePairingPort;
    }

    @BeforeEach
    void resetStubs() {
        WIRE_MOCK.resetAll();
    }

    // ── Scenario 1: device paired ─────────────────────────────────────────────

    @Test
    void checkPairingAndGetTenant_whenDevicePaired_returnsTenantId() {
        UUID deviceId = UUID.randomUUID();
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/api/v1/edge/devices/" + deviceId + "/tenant"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"tenantId\":\"tenant_abc\",\"paired\":true}")));

        String tenantId = edgePairingPort.checkPairingAndGetTenant(deviceId);

        assertThat(tenantId).isEqualTo("tenant_abc");
    }

    // ── Scenario 2: device not found (HTTP 404) ───────────────────────────────

    @Test
    void checkPairingAndGetTenant_whenDeviceNotFound_throwsDeviceNotPaired() {
        UUID deviceId = UUID.randomUUID();
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/api/v1/edge/devices/" + deviceId + "/tenant"))
                        .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("device-not-paired"));
    }

    // ── Scenario 3: edge service down (connection fault) ─────────────────────

    @Test
    void checkPairingAndGetTenant_whenEdgeDown_throwsEdgeUnavailable() {
        UUID deviceId = UUID.randomUUID();
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo("/api/v1/edge/devices/" + deviceId + "/tenant"))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> edgePairingPort.checkPairingAndGetTenant(deviceId))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("edge-unavailable"));
    }
}
