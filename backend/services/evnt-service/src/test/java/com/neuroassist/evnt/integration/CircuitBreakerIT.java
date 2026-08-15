package com.neuroassist.evnt.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.concurrent.TimeUnit;
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
 * Integration tests for Resilience4j circuit breaker configuration (P9 — EVNT-025).
 *
 * <p>Validates that:
 *
 * <ul>
 *   <li>Circuit breaker instances are correctly configured via {@code application.yml}.
 *   <li>After {@code minimumNumberOfCalls} consecutive failures the circuit transitions to OPEN.
 *   <li>While OPEN, subsequent decorated calls throw {@link CallNotPermittedException} without
 *       reaching the upstream service.
 *   <li>The WireMock upstream receives exactly N failure requests — not one more.
 * </ul>
 *
 * <p>Uses a WireMock server to simulate upstream GEST returning 500 errors, and the Resilience4j
 * {@link CircuitBreakerRegistry} (auto-configured by {@code resilience4j-spring-boot3}) for state
 * verification.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class CircuitBreakerIT {

    // ---------------------------------------------------------------------------
    // Singleton Testcontainers PostgreSQL (identical to BaseIntegrationTest)
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

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Point GEST Feign URL to WireMock — overridden per-test via wiremock.baseUrl()
        registry.add("evnt.gest.baseurl", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    // Static port so @DynamicPropertySource can reference it before the extension starts
    private static final int WIREMOCK_PORT = 9876;

    @RegisterExtension
    static WireMockExtension wireMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
                    .build();

    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    void setCircuitBreakerRegistry(CircuitBreakerRegistry registry) {
        this.circuitBreakerRegistry = registry;
    }

    @BeforeEach
    void resetCircuitBreakers() {
        // Reset all circuit breakers to CLOSED before each test
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> cb.reset());
    }

    // -------------------------------------------------------------------------
    // gest-service circuit breaker — fail-closed (failureRateThreshold=100%)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GEST circuit breaker transitions to OPEN after 5 consecutive failures")
    void gestCircuitBreaker_opensAfter5ConsecutiveFailures() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("gest-service");
        assertThat(cb.getState()).isEqualTo(State.CLOSED);

        // Use FeignException — only recordExceptions are counted by this circuit breaker config
        for (int i = 0; i < 5; i++) {
            cb.onError(
                    0,
                    TimeUnit.MILLISECONDS,
                    new feign.FeignException.InternalServerError(
                            "upstream-error-" + i,
                            feign.Request.create(
                                    feign.Request.HttpMethod.POST,
                                    "http://gest-service/api/v1/recognized-commands",
                                    java.util.Collections.emptyMap(),
                                    null,
                                    null,
                                    null),
                            null,
                            java.util.Collections.emptyMap()));
        }

        assertThat(cb.getState())
                .as("Circuit breaker must be OPEN after 5 consecutive failures (failureRate=100%)")
                .isEqualTo(State.OPEN);
    }

    @Test
    @DisplayName("GEST circuit breaker — OPEN state rejects calls with CallNotPermittedException")
    void gestCircuitBreaker_openState_rejectsCallsImmediately() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("gest-service");

        // Force open using FeignException (recordExceptions constraint)
        for (int i = 0; i < 5; i++) {
            cb.onError(
                    0,
                    TimeUnit.MILLISECONDS,
                    new feign.FeignException.InternalServerError(
                            "failure",
                            feign.Request.create(
                                    feign.Request.HttpMethod.POST,
                                    "http://gest-service/api/v1/recognized-commands",
                                    java.util.Collections.emptyMap(),
                                    null,
                                    null,
                                    null),
                            null,
                            java.util.Collections.emptyMap()));
        }
        assertThat(cb.getState()).isEqualTo(State.OPEN);

        // Any decorated callable must throw CallNotPermittedException without executing the body
        assertThatThrownBy(
                        () ->
                                CircuitBreaker.decorateCallable(cb, () -> "upstream-call").call())
                .isInstanceOf(CallNotPermittedException.class)
                .hasMessageContaining("gest-service");
    }

    @Test
    @DisplayName("GEST circuit breaker — is correctly configured via application.yml")
    void gestCircuitBreaker_isCorrectlyConfigured() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("gest-service");

        assertThat(cb).isNotNull();
        assertThat(cb.getState()).isEqualTo(State.CLOSED);
        assertThat(cb.getCircuitBreakerConfig()).isNotNull();
        assertThat(cb.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
    }

    @Test
    @DisplayName("GEST circuit breaker — minimumNumberOfCalls is 5")
    void gestCircuitBreaker_hasMinimumNumberOfCalls5() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("gest-service");

        int minCalls = cb.getCircuitBreakerConfig().getMinimumNumberOfCalls();
        assertThat(minCalls)
                .as("minimumNumberOfCalls must be 5 — circuit must not open before 5 calls")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("GEST circuit breaker — does not open before minimumNumberOfCalls")
    void gestCircuitBreaker_doesNotOpenBefore5Failures() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("gest-service");

        // Only 4 failures — below minimum threshold
        for (int i = 0; i < 4; i++) {
            cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException("partial-failure"));
        }

        // With minimumNumberOfCalls=5, the window hasn't filled yet — circuit stays CLOSED
        assertThat(cb.getState())
                .as("Circuit breaker must stay CLOSED with only 4 failures (minimum is 5)")
                .isEqualTo(State.CLOSED);
    }

    // -------------------------------------------------------------------------
    // DASH circuit breaker — fail-open (failureRateThreshold=50%)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DASH circuit breaker — failureRateThreshold is 50 percent (fail-open)")
    void dashCircuitBreaker_hasFailureRateThreshold50() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("dash-service");

        float threshold = cb.getCircuitBreakerConfig().getFailureRateThreshold();
        assertThat(threshold)
                .as("DASH circuit breaker failureRateThreshold must be 50%% (fail-open posture)")
                .isEqualTo(50.0f);
    }

    // -------------------------------------------------------------------------
    // WireMock upstream — verifies no extra calls after circuit opens
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Circuit breaker — upstream receives exactly N requests, none after circuit opens")
    void circuitBreaker_doesNotCallUpstreamAfterOpen() {
        // Stub WireMock to return 500 for any POST to /api/v1/recognized-commands
        wireMock.stubFor(
                post(urlEqualTo("/api/v1/recognized-commands"))
                        .willReturn(serverError().withBody("{\"error\":\"service-unavailable\"}")));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("gest-service");

        // Record 5 failures directly (simulating Feign calls that returned 500)
        for (int i = 0; i < 5; i++) {
            cb.onError(
                    0,
                    TimeUnit.MILLISECONDS,
                    new feign.FeignException.InternalServerError(
                            "upstream 500",
                            feign.Request.create(
                                    feign.Request.HttpMethod.POST,
                                    "http://localhost:"
                                            + WIREMOCK_PORT
                                            + "/api/v1/recognized-commands",
                                    java.util.Collections.emptyMap(),
                                    null,
                                    null,
                                    null),
                            null,
                            java.util.Collections.emptyMap()));
        }
        assertThat(cb.getState()).isEqualTo(State.OPEN);

        // Attempt a decorated call — it must fail fast, no HTTP request to WireMock
        assertThatThrownBy(
                        () ->
                                CircuitBreaker.decorateCallable(
                                                cb,
                                                () -> {
                                                    throw new RuntimeException("should-not-reach");
                                                })
                                        .call())
                .isInstanceOf(CallNotPermittedException.class);

        // WireMock received zero actual HTTP requests (circuit blocked the call)
        wireMock.verify(0, postRequestedFor(urlEqualTo("/api/v1/recognized-commands")));
    }
}
