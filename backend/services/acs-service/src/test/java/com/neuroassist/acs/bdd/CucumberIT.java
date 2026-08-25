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
// Cenarios @performance ficam FORA do gate de correcao.
//
// Eles medem relogio de parede, e num runner compartilhado isso afere a maquina
// tanto quanto o codigo: o cenario de latencia do Directory vinha reprovando
// desde 24/08 sem nenhuma regressao por tras. Um check que sempre falha e um
// check que ninguem olha — e ai o vermelho que importa passa junto.
//
// O SLA do SPEC-10 §3.2 nao foi revogado; mudou de lugar. Para rodar:
//   ./gradlew :services:acs-service:test -Dcucumber.filter.tags="@performance"
@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "not @performance")
public class CucumberIT {}
