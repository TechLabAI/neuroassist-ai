package com.neuroassist.pac.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * INT-PAC-006 (P8) — Reusable WireMock stub factory for all external PAC service integrations.
 *
 * <p>Each static method registers a specific scenario stub on the provided {@link WireMockServer}.
 * Step definitions and integration tests import these stubs to set up expected service responses.
 *
 * <p>Stub scenarios covered:
 *
 * <ul>
 *   <li><b>GEST</b>: {@code POST /api/gest/v1/detectability/check} — supported, not-supported,
 *       timeout (>500ms, triggers Feign read-timeout in test profile)
 *   <li><b>ACS</b>: {@code GET /api/acs/v1/empresas/{id}} (resolveTenant), {@code GET
 *       /api/acs/v1/cuidadores/{id}} (resolveCuidador, 404)
 *   <li><b>EDGE</b>: {@code GET /api/edge/v1/dispositivos/{id}} (resolveDispositivo, 404)
 *   <li><b>HIST</b>: {@code POST /hist-service/api/v1/facts} — 200 success, 500 failure
 * </ul>
 */
public final class PacWireMockStubs {

    private static final String APPLICATION_JSON = "application/json";

    private PacWireMockStubs() {}

    // =========================================================================
    // GEST — POST /api/gest/v1/detectability/check
    // =========================================================================

    /**
     * Stub: GEST returns {@code supported=true} for any detectability check.
     *
     * <p>Acceptance criterion (P8): routine publish with GEST unavailable → 200 with warnings. This
     * stub enables the happy-path gate check (SPEC-03 §4.1, emergency gate all-supported).
     */
    public static void stubGestDetectability_Supported(WireMockServer gest) {
        gest.stubFor(
                post(urlEqualTo("/api/gest/v1/detectability/check"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", APPLICATION_JSON)
                                        .withBody(
                                                """
                                                {"supported":true,"status":"active"}
                                                """)));
    }

    /**
     * Stub: GEST returns {@code supported=false} (primitive in "pending-training" state).
     *
     * <p>Triggers the emergency gate fail-closed path (SPEC-03 §3): emergency publish with
     * non-supported primitive → 422 {@code emergency-not-detectable}.
     */
    public static void stubGestDetectability_NotSupported(WireMockServer gest) {
        gest.stubFor(
                post(urlEqualTo("/api/gest/v1/detectability/check"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", APPLICATION_JSON)
                                        .withBody(
                                                """
                                                {"supported":false,"status":"pending-training"}
                                                """)));
    }

    /**
     * Stub: GEST responds after 1500ms — exceeds the test-profile Feign read-timeout (500ms).
     *
     * <p>P8 acceptance criterion: "WireMock stub de GEST timeout faz emergência falhar com 503".
     * The delay triggers Feign's read-timeout exception which the circuit breaker records; for
     * {@code urgencia=emergencia} the gate is fail-closed → 503 {@code
     * /errors/eligibility-gate-unavailable} + {@code Retry-After: 30}.
     */
    public static void stubGestDetectability_Timeout(WireMockServer gest) {
        gest.stubFor(
                post(urlEqualTo("/api/gest/v1/detectability/check"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", APPLICATION_JSON)
                                        .withBody(
                                                """
                                                {"supported":true,"status":"active"}
                                                """)
                                        .withFixedDelay(1500))); // 1500ms > 500ms test read-timeout
    }

    /**
     * Stub: GEST returns 503 Service Unavailable (simulates service down).
     *
     * <p>For use when testing the circuit breaker OPEN path without a timeout delay.
     */
    public static void stubGestDetectability_ServiceUnavailable(WireMockServer gest) {
        gest.stubFor(
                post(urlEqualTo("/api/gest/v1/detectability/check"))
                        .willReturn(aResponse().withStatus(503)));
    }

    // =========================================================================
    // ACS — GET /api/acs/v1/empresas/{id}  (resolveTenant)
    // =========================================================================

    /**
     * Stub: ACS resolves an empresa as valid.
     *
     * @param empresaId the empresa UUID string (used in the URL path)
     */
    public static void stubAcsResolveTenant_Valid(WireMockServer acs, String empresaId) {
        acs.stubFor(
                get(urlPathEqualTo("/api/acs/v1/empresas/" + empresaId))
                        .willReturn(
                                ok().withHeader("Content-Type", APPLICATION_JSON)
                                        .withBody(
                                                String.format(
                                                        """
                                                        {"id":"%s","valid":true}
                                                        """,
                                                        empresaId))));
    }

    /** Stub: ACS returns 404 for an unknown empresa. */
    public static void stubAcsResolveTenant_NotFound(WireMockServer acs, String empresaId) {
        acs.stubFor(
                get(urlPathEqualTo("/api/acs/v1/empresas/" + empresaId))
                        .willReturn(aResponse().withStatus(404)));
    }

    // =========================================================================
    // ACS — GET /api/acs/v1/cuidadores/{id}  (resolveCuidador)
    // =========================================================================

    /**
     * Stub: ACS resolves a cuidador as belonging to {@code empresaId}.
     *
     * @param cuidadorId the cuidador UUID string
     * @param empresaId the empresa UUID the cuidador belongs to
     */
    public static void stubAcsResolveCuidador(
            WireMockServer acs, String cuidadorId, String empresaId) {
        acs.stubFor(
                get(urlPathEqualTo("/api/acs/v1/cuidadores/" + cuidadorId))
                        .willReturn(
                                ok().withHeader("Content-Type", APPLICATION_JSON)
                                        .withBody(
                                                String.format(
                                                        """
                                                        {"id":"%s","empresaId":"%s"}
                                                        """,
                                                        cuidadorId, empresaId))));
    }

    /**
     * Stub: ACS returns 404 for an unknown cuidador.
     *
     * @param cuidadorId the cuidador UUID string to match in the path
     */
    public static void stubAcsResolveCuidador_NotFound(WireMockServer acs, String cuidadorId) {
        acs.stubFor(
                get(urlPathEqualTo("/api/acs/v1/cuidadores/" + cuidadorId))
                        .willReturn(aResponse().withStatus(404)));
    }

    /**
     * Stub: ACS resolves ALL cuidador IDs matching the path regex — catch-all for tests that don't
     * care about specific cuidador resolution (e.g. patient registration smoke tests).
     */
    public static void stubAcsResolveCuidador_AnyBelongsTo(WireMockServer acs, String empresaId) {
        acs.stubFor(
                get(urlPathMatching("/api/acs/v1/cuidadores/.*"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", APPLICATION_JSON)
                                        .withBody(
                                                String.format(
                                                        """
                                                        {"id":"00000000-0000-0000-0000-000000000001","empresaId":"%s"}
                                                        """,
                                                        empresaId))));
    }

    // =========================================================================
    // EDGE — GET /api/edge/v1/dispositivos/{id}  (resolveDispositivo)
    // =========================================================================

    /**
     * Stub: EDGE resolves a device as belonging to {@code empresaId} (unassigned to a patient).
     *
     * @param deviceId the dispositivo UUID string
     * @param empresaId the empresa UUID the device belongs to
     */
    public static void stubEdgeResolveDispositivo(
            WireMockServer edge, String deviceId, String empresaId) {
        edge.stubFor(
                get(urlPathEqualTo("/api/edge/v1/dispositivos/" + deviceId))
                        .willReturn(
                                ok().withHeader("Content-Type", APPLICATION_JSON)
                                        .withBody(
                                                String.format(
                                                        """
                                                        {"id":"%s","pacienteId":null,"empresaId":"%s"}
                                                        """,
                                                        deviceId, empresaId))));
    }

    /**
     * Stub: EDGE resolves a device as belonging to {@code empresaId} and assigned to {@code
     * pacienteId}.
     */
    public static void stubEdgeResolveDispositivo_Assigned(
            WireMockServer edge, String deviceId, String pacienteId, String empresaId) {
        edge.stubFor(
                get(urlPathEqualTo("/api/edge/v1/dispositivos/" + deviceId))
                        .willReturn(
                                ok().withHeader("Content-Type", APPLICATION_JSON)
                                        .withBody(
                                                String.format(
                                                        """
                                                        {"id":"%s","pacienteId":"%s","empresaId":"%s"}
                                                        """,
                                                        deviceId, pacienteId, empresaId))));
    }

    /** Stub: EDGE returns 404 for an unknown device. */
    public static void stubEdgeResolveDispositivo_NotFound(WireMockServer edge, String deviceId) {
        edge.stubFor(
                get(urlPathEqualTo("/api/edge/v1/dispositivos/" + deviceId))
                        .willReturn(aResponse().withStatus(404)));
    }

    /**
     * Stub: EDGE returns a device belonging to a foreign empresa (cross-tenant scenario).
     *
     * <p>Triggers the 422 validation when a patient tries to bind a device from another tenant.
     */
    public static void stubEdgeResolveDispositivo_ForeignTenant(
            WireMockServer edge, String deviceId, String foreignEmpresaId) {
        edge.stubFor(
                get(urlPathEqualTo("/api/edge/v1/dispositivos/" + deviceId))
                        .willReturn(
                                ok().withHeader("Content-Type", APPLICATION_JSON)
                                        .withBody(
                                                String.format(
                                                        """
                                                        {"id":"%s","pacienteId":null,"empresaId":"%s"}
                                                        """,
                                                        deviceId, foreignEmpresaId))));
    }

    // =========================================================================
    // HIST — POST /hist-service/api/v1/facts  (recordFact)
    // =========================================================================

    /**
     * Stub: HIST accepts domain fact recording with 200 OK.
     *
     * <p>Used for publish/recalibrate/retire scenarios where HIST is available. Feign's return type
     * is {@code void}, so the body is irrelevant — only status matters.
     */
    public static void stubHistRecordFact_Success(WireMockServer hist) {
        hist.stubFor(
                post(urlEqualTo("/hist-service/api/v1/facts"))
                        .willReturn(aResponse().withStatus(200)));
    }

    /**
     * Stub: HIST returns 500 (service failure) — triggers outbox fallback.
     *
     * <p>Verifies that lifecycle transitions succeed even when HIST is temporarily unavailable: the
     * domain fact goes to {@code hist_outbox} for retry (INT-PAC-004).
     */
    public static void stubHistRecordFact_Failure(WireMockServer hist) {
        hist.stubFor(
                post(urlEqualTo("/hist-service/api/v1/facts"))
                        .willReturn(aResponse().withStatus(500)));
    }

    /**
     * Catch-all stub: accept any request to any path on the HIST WireMock server.
     *
     * <p>Useful as a default "HIST is available" baseline when tests don't care about specifics.
     */
    public static void stubHistAvailable(WireMockServer hist) {
        hist.stubFor(any(urlPathMatching(".*")).willReturn(aResponse().withStatus(200)));
    }
}
