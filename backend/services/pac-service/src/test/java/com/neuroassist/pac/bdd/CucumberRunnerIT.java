package com.neuroassist.pac.bdd;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.jupiter.api.Tag;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Cucumber integration test runner — pac-service (PAC-025, P9).
 *
 * <p>Runs all {@code .feature} files in {@code src/test/resources/features/}. Spring context is
 * bootstrapped by {@link CucumberSpringContext} which extends {@link
 * com.neuroassist.pac.integration.BaseIntegrationTest}.
 *
 * <p>This class is the canonical test runner referenced by the plan criterion: {@code ./gradlew
 * :pac-service:test --tests "*.CucumberRunnerIT"}.
 *
 * <p>Named {@code CucumberRunnerIT} (instead of {@code CucumberIT}) so the Gradle {@code --tests}
 * filter matches. {@link CucumberIT} is kept for backward compatibility.
 */
@Tag("integration")
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.neuroassist.pac.bdd")
@ConfigurationParameter(
        key = Constants.PLUGIN_PROPERTY_NAME,
        value = "pretty, html:build/reports/cucumber/cucumber-report.html")
@ConfigurationParameter(key = Constants.EXECUTION_DRY_RUN_PROPERTY_NAME, value = "false")
public class CucumberRunnerIT {}
