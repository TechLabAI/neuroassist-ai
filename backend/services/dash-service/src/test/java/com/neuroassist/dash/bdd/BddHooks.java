package com.neuroassist.dash.bdd;

import com.neuroassist.dash.integration.BaseIntegrationTest;
import io.cucumber.java.Before;

/**
 * Cucumber lifecycle hooks — executados antes/depois de cada cenario BDD.
 *
 * <p>O @BeforeEach do JUnit nao e chamado entre cenarios Cucumber (D-scenario-isolation). Este hook
 * garante que o estado dos WireMock servers e o contexto SSE sejam limpos antes de cada cenario,
 * evitando que chamadas registradas num cenario vazem para o proximo.
 */
public class BddHooks {

    @Before
    public void resetWireMocksBeforeScenario() {
        BaseIntegrationTest.resetAndRestub();
    }
}
