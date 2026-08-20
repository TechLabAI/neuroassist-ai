package com.neuroassist.hist.bdd;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.neuroassist.hist.integration.BaseIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Bootstraps the Spring context for Cucumber step definitions. Extends BaseIntegrationTest to
 * inherit Testcontainers PostgreSQL setup (D-532: singleton container pattern for BDD test
 * context).
 *
 * <p>Also manages the singleton WireMock server for ACS actor directory stubs. The server is
 * started once in the static initializer and referenced by {@link BddWireMockHolder}.
 *
 * <p>{@code @SpringBootTest} and {@code @ActiveProfiles("test")} are inherited from
 * BaseIntegrationTest.
 */
@CucumberContextConfiguration
@Import(BddJwtConfig.class)
public class CucumberSpringContext extends BaseIntegrationTest {

    /**
     * Singleton WireMock server — ACS actor directory. Started before the Spring context boots so
     * the dynamic property registration can point feign to the correct port.
     */
    static final WireMockServer ACS_WIREMOCK;

    static {
        ACS_WIREMOCK = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        ACS_WIREMOCK.start();
    }

    /**
     * Overrides {@code neuroassist.acs.url} to point at WireMock (matches AcsActorDirectoryClient).
     */
    @DynamicPropertySource
    static void configureWireMock(DynamicPropertyRegistry registry) {
        registry.add("neuroassist.acs.url", () -> "http://localhost:" + ACS_WIREMOCK.port());
    }
}
