package com.neuroassist.dash.bdd;

import com.neuroassist.dash.integration.BaseIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;

/**
 * Bootstraps the Spring context for Cucumber step definitions. Extends BaseIntegrationTest to
 * inherit @SpringBootTest(RANDOM_PORT) + @ActiveProfiles("test"). Zero DB, zero broker (ADR-004,
 * ADR-005): dash-service e stateless no MVP.
 */
@CucumberContextConfiguration
public class CucumberSpringContext extends BaseIntegrationTest {}
