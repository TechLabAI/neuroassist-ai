package com.neuroassist.evnt.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.neuroassist.evnt.application.port.out.GestValidationPort;
import com.neuroassist.evnt.infrastructure.security.ServiceTokenProvider;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import com.neuroassist.evnt.integration.SchemaMigrations;
import feign.FeignException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
        // E2E-022 — own container; migrations must be applied before Spring starts.
        SchemaMigrations.applyTo(POSTGRES, List.of("t_emp_test"));
    }

    private static String jdbcUrlWithSearchPath() {
        String url = POSTGRES.getJdbcUrl();
        return url
                + (url.contains("?") ? "&" : "?")
                + "currentSchema=t_emp_test,neuroassist_global";
    }

    private static final int WIREMOCK_PORT = 9877;

    @RegisterExtension
    static WireMockExtension gestMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
                    .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", GestValidationContractTest::jdbcUrlWithSearchPath);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("evnt.gest.baseurl", () -> "http://localhost:" + WIREMOCK_PORT);
        // evnt-service authenticates every outbound call with a service-identity token, so the
        // token endpoint has to be reachable too — the same WireMock serves it.
        registry.add(
                "neuroassist.service-identity.token-uri",
                () -> "http://localhost:" + WIREMOCK_PORT + "/oauth2/token/service");
    }

    private static final String TENANT = "empresa_demo";

    /**
     * Binds a tenant and stubs the ACS token endpoint.
     *
     * <p>Both are now preconditions of any outbound call: the service token is minted per tenant,
     * so {@code ServiceTokenProvider} fails closed when no tenant is on the thread (RULE-07). In
     * production the tenant is bound by {@code TenantContextFilter} from the caller's JWT.
     */
    @BeforeEach
    void bindTenantAndStubTokenEndpoint() {
        gestMock.stubFor(
                post(urlEqualTo("/oauth2/token/service"))
                        .willReturn(
                                okJson(
                                        "{\"accessToken\":\"service-token\",\"tokenType\":\"Bearer\",\"expiresIn\":300}")));
        TenantContextHolder.set(TENANT);
        serviceTokenProvider.evictAll();
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private ServiceTokenProvider serviceTokenProvider;

    @Autowired
    void setServiceTokenProvider(ServiceTokenProvider provider) {
        this.serviceTokenProvider = provider;
    }

    /**
     * The production adapter, not the raw Feign client. The previous version of this test injected
     * the client and asserted against its own WireMock stub, so no production code was in the loop
     * — which is why it stayed green while the client pointed at a path gest-service does not
     * expose.
     */
    private GestValidationPort adapter;

    @Autowired
    void setAdapter(GestValidationPort adapter) {
        this.adapter = adapter;
    }

    // -------------------------------------------------------------------------
    // Scenario 1 — the primitive exists: 200 — the adapter accepts the report
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S1: Known primitive — GEST returns 200 — adapter validates the report")
    void knownPrimitive_isAccepted() {
        UUID primitive = UUID.randomUUID();
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/tipo-gesto/" + primitive))
                        .willReturn(
                                okJson(
                                        "{\"id\":\""
                                                + primitive
                                                + "\",\"nome\":\"piscar-duplo\",\"detectabilityState\":\"detectavel\"}")));

        boolean valid =
                adapter.validate(
                        new GestValidationPort.GestValidationRequest(
                                1, List.of(primitive.toString())));

        assertThat(valid).isTrue();
    }

    // -------------------------------------------------------------------------
    // Scenario 2 — the primitive is unknown: 404 is an answer, not a failure
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S2: Unknown primitive — GEST returns 404 — adapter rejects the report")
    void unknownPrimitive_isRejected() {
        UUID primitive = UUID.randomUUID();
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/tipo-gesto/" + primitive))
                        .willReturn(aResponse().withStatus(404)));

        boolean valid =
                adapter.validate(
                        new GestValidationPort.GestValidationRequest(
                                1, List.of(primitive.toString())));

        assertThat(valid).isFalse();
    }

    // -------------------------------------------------------------------------
    // Scenario 3 — every primitive is checked, and one unknown rejects the report
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S3: One unknown primitive among several rejects the whole report")
    void oneUnknownPrimitiveRejectsTheReport() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/tipo-gesto/" + known))
                        .willReturn(okJson("{\"id\":\"" + known + "\",\"nome\":\"piscar\"}")));
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/tipo-gesto/" + unknown))
                        .willReturn(aResponse().withStatus(404)));

        boolean valid =
                adapter.validate(
                        new GestValidationPort.GestValidationRequest(
                                1, List.of(known.toString(), unknown.toString())));

        assertThat(valid).isFalse();
        gestMock.verify(getRequestedFor(urlEqualTo("/gest-service/tipo-gesto/" + known)));
    }

    // -------------------------------------------------------------------------
    // Scenario 4 — a GEST outage is NOT laundered into "the gesture is invalid"
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S4: GEST returns 500 — the failure propagates instead of becoming a rejection")
    void gestOutageIsNotSilentlyTreatedAsInvalid() {
        UUID primitive = UUID.randomUUID();
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/tipo-gesto/" + primitive))
                        .willReturn(aResponse().withStatus(500)));

        // Defect C-19 in reverse: a fallback that answered "invalid" here would make an outage
        // indistinguishable from a patient whose gesture is not recognised.
        assertThatThrownBy(
                        () ->
                                adapter.validate(
                                        new GestValidationPort.GestValidationRequest(
                                                1, List.of(primitive.toString()))))
                .isInstanceOf(FeignException.class);
    }

    // -------------------------------------------------------------------------
    // Scenario 5 — X-Correlation-Id header propagation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("S5: Outbound request carries X-Correlation-Id header (MDC propagation)")
    void outboundRequest_carriesCorrelationIdHeader() {
        UUID primitive = UUID.randomUUID();
        gestMock.stubFor(
                get(urlEqualTo("/gest-service/tipo-gesto/" + primitive))
                        .willReturn(okJson("{\"id\":\"" + primitive + "\",\"nome\":\"piscar\"}")));

        org.slf4j.MDC.put("correlationId", "test-corr-id-001");
        try {
            adapter.validate(
                    new GestValidationPort.GestValidationRequest(1, List.of(primitive.toString())));
            gestMock.verify(
                    getRequestedFor(urlEqualTo("/gest-service/tipo-gesto/" + primitive))
                            .withHeader("X-Correlation-Id", equalTo("test-corr-id-001")));
        } finally {
            org.slf4j.MDC.remove("correlationId");
        }
    }
}
