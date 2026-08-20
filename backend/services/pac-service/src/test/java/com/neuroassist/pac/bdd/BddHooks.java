package com.neuroassist.pac.bdd;

import com.neuroassist.pac.integration.ExternalServicesWireMock;
import io.cucumber.java.Before;
import lombok.RequiredArgsConstructor;

/**
 * Cucumber lifecycle hooks for PAC BDD tests (PAC-025, P9).
 *
 * <p>Resets per-scenario state in {@link ScenarioContext} and WireMock stubs before each scenario
 * so that state from one scenario cannot bleed into another.
 *
 * <p>Not annotated with @Component — registered exclusively via {@link BddTestConfiguration}.
 */
@RequiredArgsConstructor
public class BddHooks {

    private final ScenarioContext scenarioContext;

    /**
     * Resets all scenario state and WireMock stubs before each scenario.
     *
     * <p>Order 0 ensures this runs before any step-class {@code @Before} hooks.
     */
    @Before(order = 0)
    public void beforeScenario() {
        scenarioContext.reset();
        ExternalServicesWireMock.resetAll();
    }
}
