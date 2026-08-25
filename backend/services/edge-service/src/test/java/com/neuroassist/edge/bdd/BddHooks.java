package com.neuroassist.edge.bdd;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.neuroassist.edge.infrastructure.security.ServiceTokenProvider;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cucumber lifecycle hooks for BDD tests. Runs before/after each scenario to ensure isolation:
 *
 * <ul>
 *   <li>{@code @Before} — truncates tenant tables + resets WireMock stubs + re-installs the default
 *       ACS service-token stub.
 *   <li>{@code @After} — resets the Resilience4j circuit breaker for {@code gest-detection-profile}
 *       and invalidates the {@link ServiceTokenProvider} cache for the test tenant, so state does
 *       not leak between scenarios.
 * </ul>
 *
 * <p>All DB operations use fully qualified {@code t_emp_test.*} table names because {@code
 * JdbcTemplate} bypasses Hibernate's multi-tenancy and operates against the raw DataSource.
 */
public class BddHooks {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired private ServiceTokenProvider serviceTokenProvider;

    /** Name of the Resilience4j circuit breaker protecting the heartbeat GEST profile call. */
    private static final String CB_GEST = "gest-detection-profile";

    /** Name of the Resilience4j circuit breaker for GET /api/v1/edge/profile (EDGE-022). */
    private static final String CB_DEVICE_PROFILE = "edge-device-profile";

    @Before
    public void cleanUpAndResetStubs() {
        // Truncate all tenant-schema tables. CASCADE handles FK references
        // (dispositivo_enrollment, dispositivo_credential, dispositivo_edge_transition).
        jdbcTemplate.update("TRUNCATE t_emp_test.dispositivo_edge CASCADE");

        // Truncate global index tables so enrollment and device directory entries from previous
        // scenarios do not interfere. These are schema-qualified so the raw DataSource connection
        // (no search_path override) can always reach them.
        jdbcTemplate.update("TRUNCATE neuroassist_global.enrollment_directory");
        jdbcTemplate.update("TRUNCATE neuroassist_global.device_directory");

        // Reset WireMock to remove any stubs added in previous scenarios.
        CucumberSpringContext.WIRE_MOCK.resetAll();

        // Re-install the default ACS service-token stub so any heartbeat-triggered
        // GEST Feign call (which needs a service token first) succeeds by default.
        stubAcsServiceToken();
    }

    @After
    public void resetServiceState() {
        // Invalidate cached service token so the next scenario gets a fresh one.
        serviceTokenProvider.invalidate(TestJwtFactory.TEST_TENANT);

        // Reset circuit breakers so "CB open" tests do not bleed into later scenarios.
        resetCircuitBreaker(CB_GEST);
        resetCircuitBreaker(CB_DEVICE_PROFILE);
    }

    private void resetCircuitBreaker(String name) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
            if (cb.getState() != CircuitBreaker.State.CLOSED) {
                cb.reset();
            }
        } catch (Exception ignored) {
            // CB might not have been created yet if the relevant operation has not run.
        }
    }

    /**
     * Default ACS stub: {@code POST /oauth2/token/service} → 200 with a synthetic service token.
     * Re-installed after every {@code WIRE_MOCK.resetAll()} so the EDGE service-identity flow
     * always has a valid token to attach to GEST Feign calls.
     */
    static void stubAcsServiceToken() {
        CucumberSpringContext.WIRE_MOCK.stubFor(
                post(urlEqualTo("/oauth2/token/service"))
                        .willReturn(
                                ok().withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"accessToken\":\"test-svc-token\","
                                                        + "\"tokenType\":\"Bearer\","
                                                        + "\"expiresIn\":900}")));
    }
}
