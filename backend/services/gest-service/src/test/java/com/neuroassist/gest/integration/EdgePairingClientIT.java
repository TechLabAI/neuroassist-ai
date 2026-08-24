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
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
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
        // A propriedade e `edge.pairing.url` — o nome que @FeignClient realmente le. Registrar
        // "gest.edge.url", como estava, nao sobrescrevia nada: o cliente caia no default
        // http://edge-service:8087, um nome de container, e o teste tentava resolve-lo a partir do
        // host. Falhava sempre, e por um motivo que nada tinha a ver com o que ele testa (E2E-034).
        registry.add("edge.pairing.url", WIRE_MOCK::baseUrl);
        // O cliente passou a usar a identidade de servico do GEST (E2E-034), entao o proprio
        // endpoint de token do ACS tambem e servido pelo WireMock aqui.
        registry.add("neuroassist.acs.service-token.url", WIRE_MOCK::baseUrl);
        registry.add("neuroassist.acs.service-token.client-id", () -> "gest-service");
        registry.add("neuroassist.acs.service-token.client-secret", () -> "test-secret");
    }

    private EdgePairingPort edgePairingPort;

    @Autowired
    void setEdgePairingPort(EdgePairingPort edgePairingPort) {
        this.edgePairingPort = edgePairingPort;
    }

    @BeforeEach
    void resetStubs() {
        WIRE_MOCK.resetAll();
        // O ACS emite o token de identidade de servico. Sem este stub o cliente falha FECHADO —
        // que e o comportamento correto e o que este @BeforeEach documenta: nao ha fallback
        // silencioso para o JWT do chamador.
        WIRE_MOCK.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(
                                urlPathEqualTo("/oauth2/token/service"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"accessToken\":\"service-token-de-teste\","
                                                    + "\"tokenType\":\"Bearer\",\"expiresIn\":900}")));
        // RULE-07: o token e escopado por tenant, e o provider recusa inventar um default.
        TenantContextHolder.set("t_emp_test");
    }

    @org.junit.jupiter.api.AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
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
                                                // Espelha TenantRefView do edge-service. O stub
                                                // dizia {"tenantId":..,"paired":true} — dois
                                                // campos que o EDGE nunca enviou. Escrito com a
                                                // mesma suposicao do DTO do consumidor, passava
                                                // exatamente porque a suposicao estava errada.
                                                "{\"deviceId\":\""
                                                        + deviceId
                                                        + "\",\"tenantRef\":\"tenant_abc\"}")));

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
