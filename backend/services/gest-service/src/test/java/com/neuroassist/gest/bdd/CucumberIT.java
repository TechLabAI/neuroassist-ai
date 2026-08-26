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
// Cenarios marcados @wip descrevem capacidade que o servico NAO tem — nao foram deixados de fora
// por serem dificeis, e sim porque implementa-los contra o codigo atual so poderia produzir um
// step que nao falha. Cada um esta justificado no proprio .feature, ao lado da tag.
@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
public class CucumberIT {}
