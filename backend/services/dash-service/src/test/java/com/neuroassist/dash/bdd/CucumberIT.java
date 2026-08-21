package com.neuroassist.dash.bdd;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.*;

/**
 * Cucumber integration test runner — dash-service.
 *
 * <p>Runs all .feature files in src/test/resources/features/. Requires a running Spring context
 * (see CucumberSpringContext). DRY_RUN removido em P8: todos os steps sao executados.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.neuroassist.dash.bdd")
@ConfigurationParameter(
        key = Constants.PLUGIN_PROPERTY_NAME,
        value = "pretty, html:build/reports/cucumber/cucumber-report.html")
public class CucumberIT {}
