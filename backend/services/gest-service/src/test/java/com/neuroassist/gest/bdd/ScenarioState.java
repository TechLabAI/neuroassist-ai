package com.neuroassist.gest.bdd;

import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Per-scenario shared state for BDD step definitions. Scoped to "cucumber-glue" so a fresh instance
 * is created for each scenario. Holds the last HTTP response and entity ID produced by When steps,
 * consumed by Then assertion steps.
 */
@Component
@Scope("cucumber-glue")
public class ScenarioState {

    /** Last HTTP response received from the service under test. */
    public ResponseEntity<String> lastResponse;

    /** UUID string of the last TipoGesto created or transitioned in the current scenario. */
    public String lastEntityId;

    /** Basic-auth credentials (user:password) for the current scenario caller. */
    public String currentCredentials = "gest-manager:manager-secret";
}
