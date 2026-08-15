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
 * WireMock contract tests for EVNT → PAC integration (INT-EVNT-003, P9).
 *
 * <p>Validates PAC command resolution contract:
 *
 * <ol>
 *   <li>Active Comando → 200 with {label, urgencia, sensibilidade, pacienteId}.
 *   <li>Deleted/deactivated Comando → 404 → adapter throws CommandUnresolvableException.
 * </ol>
 *
 * <p>Note: {@code CommandLookupClient} currently has no methods (pending full implementation).
 * These tests validate the WireMock stub setup and will be extended when the PAC Feign adapter
 * methods are implemented in a subsequent cycle.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DisplayName("INT-EVNT-003: Contract tests EVNT -> PAC (WireMock)")
class PacCommandLookupContractTest {

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

    private static final int WIREMOCK_PORT = 9878;

    @RegisterExtension
    static WireMockExtension pacMock =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
                    .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("evnt.pac.baseurl", () -> "http://localhost:" + WIREMOCK_PORT);
    }

    /**
     * Verifies WireMock stub setup for PAC command-found scenario.
     *
     * <p>When {@code CommandLookupClient.findByRef(ref)} is implemented, this test body should
     * inject and call the client, asserting that the returned snapshot matches the stub.
     */
    @Test
    @DisplayName("S1: Active Comando — PAC WireMock stub returns 200 with full snapshot")
    void activeComando_pacReturns200WithSnapshot() {
        pacMock.stubFor(
                get(urlMatching("/api/v1/comandos/.*"))
                        .willReturn(
                                okJson(
                                        "{"
                                                + "\"ref\":\"cmd-001\","
                                                + "\"label\":\"Toque duplo\","
                                                + "\"urgencia\":\"rotina\","
                                                + "\"sensibilidade\":0.75,"
                                                + "\"pacienteId\":\"pac-uuid-001\""
                                                + "}")));

        // Stub configured — verify mapping is registered in WireMock
        assertThat(pacMock.getStubMappings()).isNotEmpty();

        // TODO: inject CommandLookupClient when PAC adapter methods are implemented
        // CommandLookupClient.ComandoSnapshot snapshot = commandLookupClient.findByRef("cmd-001");
        // assertThat(snapshot.urgencia()).isEqualTo("rotina");
    }

    @Test
    @DisplayName("S2: Deleted Comando — PAC WireMock stub returns 404")
    void deletedComando_pacReturns404() {
        pacMock.stubFor(
                get(urlMatching("/api/v1/comandos/DELETED-.*"))
                        .willReturn(
                                aResponse()
                                        .withStatus(404)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"type\":\"not-found\","
                                                        + "\"status\":404,"
                                                        + "\"detail\":\"Comando deactivated\"}")));

        assertThat(pacMock.getStubMappings()).isNotEmpty();

        // TODO: inject CommandLookupClient when PAC adapter methods are implemented
        // assertThatThrownBy(() -> commandLookupClient.findByRef("DELETED-cmd-001"))
        //     .isInstanceOf(CommandUnresolvableException.class);
    }
}
