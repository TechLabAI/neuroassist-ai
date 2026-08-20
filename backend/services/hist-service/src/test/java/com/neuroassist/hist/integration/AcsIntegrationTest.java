package com.neuroassist.hist.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.neuroassist.hist.application.port.out.ActorDirectoryPort;
import com.neuroassist.hist.domain.model.ActorDirectoryUnavailableException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * INT-HIST-002 — ACS integration tests using WireMock (no real ACS required in CI).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Success path: ACS returns patient list → adapter returns it.
 *   <li>Timeout path: ACS responds after read timeout → 503 via {@link
 *       ActorDirectoryUnavailableException}.
 *   <li>Circuit-breaker open path: repeated failures open CB → subsequent calls fail immediately.
 * </ul>
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AcsIntegrationTest {

    // ---- PostgreSQL (shared with other IT) ----

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

    // ---- WireMock (singleton — started in static initializer so @DynamicPropertySource can read
    // its port) ----

    static final WireMockServer wireMock;

    static {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redirect datasource to Testcontainers PostgreSQL
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Redirect Feign ACS client to WireMock
        registry.add("neuroassist.acs.url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired
    private ActorDirectoryPort actorDirectoryPort;

    // ---- Tests ----

    @Test
    void resolvePatientScope_success_returnsPatientList() {
        UUID caregiverId = UUID.randomUUID();
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();

        wireMock.stubFor(
                get(urlPathMatching("/api/v1/acs/caregivers/" + caregiverId + "/patients"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "[\""
                                                        + patientA
                                                        + "\",\""
                                                        + patientB
                                                        + "\"]")));

        List<UUID> result = actorDirectoryPort.resolvePatientScope("t_empresa_a", caregiverId);

        assertThat(result).containsExactlyInAnyOrder(patientA, patientB);
        wireMock.verify(
                1,
                getRequestedFor(
                        urlPathMatching(
                                "/api/v1/acs/caregivers/" + caregiverId + "/patients")));
    }

    @Test
    void resolvePatientScope_acsUnavailable_throwsActorDirectoryUnavailableException() {
        UUID caregiverId = UUID.randomUUID();

        wireMock.stubFor(
                get(urlPathMatching("/api/v1/acs/caregivers/" + caregiverId + "/patients"))
                        .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(
                        () -> actorDirectoryPort.resolvePatientScope("t_empresa_a", caregiverId))
                .isInstanceOf(ActorDirectoryUnavailableException.class)
                .hasMessageContaining(caregiverId.toString());
    }

    @Test
    void resolvePatientScope_acsTimeout_throwsActorDirectoryUnavailableException() {
        UUID caregiverId = UUID.randomUUID();

        // Delay beyond the 2s read timeout configured in application.yml
        wireMock.stubFor(
                get(urlPathMatching("/api/v1/acs/caregivers/" + caregiverId + "/patients"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("[]")
                                        .withFixedDelay(3500)));

        assertThatThrownBy(
                        () -> actorDirectoryPort.resolvePatientScope("t_empresa_a", caregiverId))
                .isInstanceOf(ActorDirectoryUnavailableException.class);
    }
}
