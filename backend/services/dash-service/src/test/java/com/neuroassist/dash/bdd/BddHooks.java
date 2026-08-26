package com.neuroassist.dash.bdd;

import com.neuroassist.dash.integration.BaseIntegrationTest;
import io.cucumber.java.Before;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Cucumber lifecycle hooks — executados antes/depois de cada cenario BDD.
 *
 * <p>O @BeforeEach do JUnit nao e chamado entre cenarios Cucumber (D-scenario-isolation). Este hook
 * garante que o estado dos WireMock servers e o contexto SSE sejam limpos antes de cada cenario,
 * evitando que chamadas registradas num cenario vazem para o proximo.
 */
public class BddHooks {

    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @Before
    public void resetWireMocksBeforeScenario() {
        BaseIntegrationTest.resetAndRestub();
        resetCircuitBreakers();
    }

    /**
     * Os breakers introduzidos em E2E-018 sao estado COMPARTILHADO entre cenarios: um cenario que
     * derruba o EVNT de proposito deixa o breaker aberto, e o cenario seguinte recebe 503 sem ter
     * derrubado nada.
     *
     * <p>Sem este reset a suite passaria a depender da ORDEM dos cenarios — que e o tipo de
     * acoplamento que faz um teste falhar sozinho e passar quando rodado isolado.
     */
    private void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> cb.transitionToClosedState());
    }
}
