package com.neuroassist.gest.bdd;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.neuroassist.gest.bdd")
@ConfigurationParameter(
        key = Constants.PLUGIN_PROPERTY_NAME,
        value = "pretty, html:build/reports/cucumber/cucumber-report.html")
@ConfigurationParameter(key = Constants.EXECUTION_DRY_RUN_PROPERTY_NAME, value = "false")
public class CucumberIT {}
