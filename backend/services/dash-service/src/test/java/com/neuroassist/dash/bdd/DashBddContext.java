package com.neuroassist.dash.bdd;

import io.cucumber.spring.ScenarioScope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Contexto compartilhado entre step definitions BDD do dash-service.
 *
 * <p>Escopo {@code cucumber-glue} (ScenarioScope) garante uma nova instancia por cenario, evitando
 * vazamento de estado entre cenarios.
 */
@Component
@ScenarioScope
public class DashBddContext {

    // ---- identidade ----
    public String caregiverId = "jamilly";
    public String tenantId = "familia-amaral";

    // ---- tokens JWT gerados pelo JwtTestHelper ----
    public String jamillyToken;
    public String evntServiceToken;

    // ---- resposta HTTP do ultimo request ----
    public ResponseEntity<String> lastResponse;

    // ---- estado de cenario ----
    /** ID do paciente corrente no cenario. */
    public String currentPatientId = "Marcio";

    /** Indica se uma sessao SSE foi aberta por este cenario. */
    public boolean sseSessionOpen = false;
}
