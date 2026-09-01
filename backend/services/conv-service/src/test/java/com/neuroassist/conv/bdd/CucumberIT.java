package com.neuroassist.conv.bdd;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.*;

/**
 * Cucumber integration test runner — conv-service.
 *
 * <p>Runs all .feature files in src/test/resources/features/. Requires a running Spring context
 * (see CucumberSpringContext).
 *
 * <p>DRY-RUN mode is active: stub steps with PendingException are SKIPPED, not FAILED. Remove
 * EXECUTION_DRY_RUN_PROPERTY_NAME once steps are wired.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.neuroassist.conv.bdd")
@ConfigurationParameter(
        key = Constants.PLUGIN_PROPERTY_NAME,
        value = "pretty, html:build/reports/cucumber/cucumber-report.html")
@ConfigurationParameter(key = Constants.EXECUTION_DRY_RUN_PROPERTY_NAME, value = "true")
public class CucumberIT {}
