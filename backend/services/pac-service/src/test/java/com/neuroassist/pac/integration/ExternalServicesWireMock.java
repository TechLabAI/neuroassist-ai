package com.neuroassist.pac.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.neuroassist.pac.bdd.TestJwtHelper;

/**
 * INT-PAC-006 (P8) — WireMock server lifecycle for all external PAC integrations.
 *
 * <p>PASSO-0 fix: switched from fixed ports (19991-19994) to dynamic ports to prevent
 * FatalStartupException on port conflicts. Ports are resolved at startup and injected into Spring
 * properties via {@code @DynamicPropertySource} in {@link BaseIntegrationTest}.
 *
 * <p>Servers:
 *
 * <ul>
 *   <li>GEST — {@code /api/gest/v1/detectability/check}
 *   <li>ACS — {@code /api/acs/v1/empresas/{id}}, {@code /api/acs/v1/cuidadores/{id}}
 *   <li>EDGE — {@code /api/edge/v1/dispositivos/{id}}
 *   <li>HIST — {@code /hist-service/api/v1/facts}
 *   <li>JWK — {@code /.well-known/jwks.json} (RSA public key for OAuth2 JWT validation)
 * </ul>
 *
 * <p>Singleton pattern: servers are started once per JVM. Call {@link #resetAll()} in
 * {@code @BeforeEach} to clear stubs between tests.
 */
public final class ExternalServicesWireMock {

    private static final WireMockServer gestServer;
    private static final WireMockServer acsServer;
    private static final WireMockServer edgeServer;
    private static final WireMockServer histServer;
    private static final WireMockServer jwkServer;

    static {
        gestServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        acsServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        edgeServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        histServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        jwkServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

        gestServer.start();
        acsServer.start();
        edgeServer.start();
        histServer.start();
        jwkServer.start();

        // Pre-stub JWK endpoint so Spring Security can fetch RSA public keys at startup.
        jwkServer.stubFor(
                get(urlEqualTo("/.well-known/jwks.json"))
                        .willReturn(okJson(TestJwtHelper.getJwksJson())));
    }

    private ExternalServicesWireMock() {}

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts all servers if not already running. Safe to call multiple times (idempotent).
     *
     * @deprecated Servers are now started in the static initializer; this method is kept for
     *     backward compatibility but is a no-op.
     */
    @Deprecated
    public static synchronized void startAll() {
        // Servers are already started in the static initializer.
    }

    /** Stops all running WireMock servers. */
    public static synchronized void stopAll() {
        stopIfRunning(gestServer);
        stopIfRunning(acsServer);
        stopIfRunning(edgeServer);
        stopIfRunning(histServer);
        stopIfRunning(jwkServer);
    }

    /**
     * Clears all registered stubs on GEST/ACS/EDGE/HIST servers and re-stubs the JWK endpoint. Call
     * in {@code @BeforeEach} to start each test with a clean slate.
     */
    public static void resetAll() {
        gestServer.resetAll();
        // Default stub: any detectability check returns supported=true.
        // Prevents WireMock's 404 from triggering FeignException.NotFound → 422.
        // Scenario-specific stubs (timeout, 503, not-supported) override this.
        PacWireMockStubs.stubGestDetectability_Supported(gestServer);
        acsServer.resetAll();
        edgeServer.resetAll();
        histServer.resetAll();
        // Do NOT reset jwkServer — the JWK stub must remain active across all tests.
    }

    // -------------------------------------------------------------------------
    // Server accessors
    // -------------------------------------------------------------------------

    /** Returns the GEST WireMock server for stub registration. */
    public static WireMockServer gest() {
        return gestServer;
    }

    /** Returns the ACS WireMock server for stub registration. */
    public static WireMockServer acs() {
        return acsServer;
    }

    /** Returns the EDGE WireMock server for stub registration. */
    public static WireMockServer edge() {
        return edgeServer;
    }

    /** Returns the HIST WireMock server for stub registration. */
    public static WireMockServer hist() {
        return histServer;
    }

    /** Returns the JWK WireMock server (for JWKS endpoint). */
    public static WireMockServer jwk() {
        return jwkServer;
    }

    // -------------------------------------------------------------------------
    // Base URL helpers — injected into Spring properties via @DynamicPropertySource
    // -------------------------------------------------------------------------

    public static String gestBaseUrl() {
        return "http://localhost:" + gestServer.port();
    }

    public static String acsBaseUrl() {
        return "http://localhost:" + acsServer.port();
    }

    public static String edgeBaseUrl() {
        return "http://localhost:" + edgeServer.port();
    }

    public static String histBaseUrl() {
        return "http://localhost:" + histServer.port();
    }

    public static String jwkBaseUrl() {
        return "http://localhost:" + jwkServer.port();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void stopIfRunning(WireMockServer server) {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }
}
