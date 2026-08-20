package com.neuroassist.acs.bdd;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.*;

/**
 * Cucumber integration test runner — acs-service.
 *
 * <p>Runs all .feature files in src/test/resources/features/. Requires a running Spring context
 * (see CucumberSpringContext).
 *
 * <p>P5 (ACS-024): dry-run removed — step definitions are fully implemented.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.neuroassist.acs.bdd")
@ConfigurationParameter(
        key = Constants.PLUGIN_PROPERTY_NAME,
        value = "pretty, html:build/reports/cucumber/cucumber-report.html")
public class CucumberIT {}
