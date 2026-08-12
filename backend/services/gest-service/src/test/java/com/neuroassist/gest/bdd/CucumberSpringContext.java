package com.neuroassist.gest.bdd;

import com.neuroassist.gest.integration.BaseIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;

/**
 * Bootstraps the Spring context for Cucumber step definitions. Extends {@link BaseIntegrationTest}
 * for the same singleton Testcontainers PostgreSQL wiring the other integration tests use — without
 * it, the context falls back to the "test" profile's default datasource (localhost:5432), which
 * isn't running in CI/dev and fails context load entirely.
 */
@CucumberContextConfiguration
public class CucumberSpringContext extends BaseIntegrationTest {}
