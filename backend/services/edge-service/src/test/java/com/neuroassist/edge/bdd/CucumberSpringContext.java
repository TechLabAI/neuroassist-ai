package com.neuroassist.edge.bdd;

import com.neuroassist.edge.integration.BaseIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;

/**
 * Bootstraps the Spring context for Cucumber step definitions. Extends BaseIntegrationTest to
 * inherit Testcontainers PostgreSQL setup (D-532: singleton container pattern for BDD test
 * context).
 *
 * <p>@SpringBootTest and @ActiveProfiles("test") are inherited from BaseIntegrationTest.
 */
@CucumberContextConfiguration
public class CucumberSpringContext extends BaseIntegrationTest {}
