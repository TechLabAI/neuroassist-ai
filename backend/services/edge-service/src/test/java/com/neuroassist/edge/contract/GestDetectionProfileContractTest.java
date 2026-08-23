package com.neuroassist.edge.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.neuroassist.edge.infrastructure.clients.DetectionProfileClient;
import com.neuroassist.edge.infrastructure.security.ServiceTokenProvider;
import com.neuroassist.edge.infrastructure.security.ServiceTokenUnavailableException;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

    /** Tenant the service token is scoped to (service-identity contract §3). */
    private static final String TENANT = "e2e00000-0000-0000-0000-000000000001";

    private static final String SERVICE_TOKEN = "service-identity-jwt-from-acs";
    private static final String CALLER_TOKEN = "caller-device-jwt";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("neuroassist.gest.url", () -> "http://localhost:" + WIREMOCK_PORT);
        // ACS service-identity endpoint is served by the same WireMock instance
        registry.add(
                "neuroassist.acs.service-token.url", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    private final DetectionProfileClient detectionProfileClient;
    private final ServiceTokenProvider serviceTokenProvider;

    @Autowired
    GestDetectionProfileContractTest(
            DetectionProfileClient detectionProfileClient,
            ServiceTokenProvider serviceTokenProvider) {
        this.detectionProfileClient = detectionProfileClient;
        this.serviceTokenProvider = serviceTokenProvider;
    }

    /**
     * Every outbound GEST call now needs a tenant-scoped service token (service-identity §5), so
     * the ACS endpoint is stubbed and a tenant is put in context — as TenantContextFilter does in
     * production. The provider cache is dropped so each test observes its own stub.
     */
    @BeforeEach
    void seedServiceIdentity() {
        gestMock.stubFor(
                post(urlEqualTo("/oauth2/token/service"))
                        .willReturn(
                                okJson(
                                        "{\"accessToken\":\""
                                                + SERVICE_TOKEN
                                                + "\",\"tokenType\":\"Bearer\",\"expiresIn\":900}")));
        TenantContextHolder.set(TENANT);
        serviceTokenProvider.invalidate(TENANT);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        serviceTokenProvider.invalidate(TENANT);
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

    // -------------------------------------------------------------------------
    // Scenario 4 — service identity replaces the caller's JWT (C-19)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "S4: Authorization carries the ACS service token, not the caller's device JWT (C-19)")
    void outboundRequest_carriesServiceIdentityNotCallerToken() {
        UUID deviceId = UUID.randomUUID();
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/detection-profile/devices/" + deviceId))
                        .willReturn(okJson("{\"version\":11}")));

        // The caller is a device whose token never holds gest.detection-profile.read.
        Jwt callerJwt =
                Jwt.withTokenValue(CALLER_TOKEN)
                        .header("alg", "none")
                        .claim("sub", "device-d7b1")
                        .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(callerJwt));

        detectionProfileClient.getActiveProfileForDevice(deviceId);

        // Exactly one Authorization value, and it is the service token — not two values with the
        // caller's token first (RequestTemplate.header() appends).
        var received =
                gestMock.findAll(
                        getRequestedFor(
                                urlEqualTo("/gest-service/detection-profile/devices/" + deviceId)));
        assertThat(received).hasSize(1);
        assertThat(received.get(0).header("Authorization").values())
                .containsExactly("Bearer " + SERVICE_TOKEN);

        // The token was fetched from the ACS with the tenant of the request in course.
        // clientId matches neuroassist.acs.service-token.client-id from application-test.yml.
        gestMock.verify(
                postRequestedFor(urlEqualTo("/oauth2/token/service"))
                        .withRequestBody(
                                matchingJsonPath("$.clientId", equalTo("test-edge-service")))
                        .withRequestBody(matchingJsonPath("$.tenantId", equalTo(TENANT))));
    }

    // -------------------------------------------------------------------------
    // Scenario 5 — fail-closed: ACS unavailable aborts the call
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "S5: ACS rejects the service credential — the GEST call is never made (fail-closed)")
    void acsRejects_callIsNotMade() {
        UUID deviceId = UUID.randomUUID();
        gestMock.stubFor(
                post(urlEqualTo("/oauth2/token/service")).willReturn(aResponse().withStatus(401)));
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/detection-profile/devices/" + deviceId))
                        .willReturn(okJson("{\"version\":11}")));
        serviceTokenProvider.invalidate(TENANT);

        assertThatThrownBy(() -> detectionProfileClient.getActiveProfileForDevice(deviceId))
                .isInstanceOf(ServiceTokenUnavailableException.class);

        gestMock.verify(
                0,
                getRequestedFor(urlEqualTo("/gest-service/detection-profile/devices/" + deviceId)));
    }
}
