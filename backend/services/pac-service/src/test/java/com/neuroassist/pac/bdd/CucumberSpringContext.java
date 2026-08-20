package com.neuroassist.pac.bdd;

import com.neuroassist.pac.integration.BaseIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Bootstraps the Spring context for Cucumber step definitions. Extends BaseIntegrationTest to
 * inherit Testcontainers PostgreSQL setup (D-532: singleton container pattern for BDD test
 * context).
 *
 * <p>{@code @SpringBootTest(webEnvironment=RANDOM_PORT)} and {@code @ActiveProfiles("test")} and
 * {@code @DynamicPropertySource} are all inherited from BaseIntegrationTest.
 *
 * <p>{@link BddTestConfiguration} is imported here (not via component-scan) so its beans — step
 * definitions, HttpTestHelper, ScenarioContext, etc. — are ONLY present in this BDD context. This
 * prevents TestRestTemplate-dependent beans from being created in other test contexts such as
 * SecurityConfigTest which uses webEnvironment=MOCK and has no TestRestTemplate.
 */
@CucumberContextConfiguration
@Import(BddTestConfiguration.class)
public class CucumberSpringContext extends BaseIntegrationTest {}
