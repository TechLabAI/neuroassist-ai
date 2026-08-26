package com.neuroassist.gest.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.neuroassist.gest.bdd.BddTestContext;
import com.neuroassist.gest.bdd.GestHttp;
import com.neuroassist.gest.bdd.ScenarioState;
import com.neuroassist.gest.bdd.TestJwtFactory;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Steps do guarda de comando de emergência — {@code eligibility-check.feature}.
 *
 * <p>O endpoint é somente leitura, e "somente leitura" é uma afirmação verificável: os steps de
 * idempotência comparam o estado do banco antes e depois, e não apenas os dois corpos de resposta.
 * Um serviço que gravasse uma transição a cada consulta devolveria as mesmas duas respostas.
 */
@Slf4j
@RequiredArgsConstructor
public class EligibilityCheckSteps {

    private static final String PATH = "/gest-service/eligibility";

    private final GestHttp http;
    private final ScenarioState state;
    private final BddTestContext db;

    private String estadoAntes;
    private int transicoesAntes;

    // ── Given ────────────────────────────────────────────────────────────────

    @Given("primitive {string} is in state {string} in tenant {string}")
    public void primitivoEmEstadoNoTenant(String nome, String estado, String tenant) {
        String schema = db.schemaFor(db.mapTenantName(tenant));
        String ref = "supported".equals(estado) ? "mp." + nome + ".v1" : null;
        db.insertTipoGesto(nome, estado, ref, schema);
    }

    @And("primitive {string} exists in tenant {string} in state {string}")
    public void primitivoExisteNoTenantEmEstado(String nome, String tenant, String estado) {
        primitivoEmEstadoNoTenant(nome, estado, tenant);
    }

    /**
     * Token válido, com tenant, e sem {@code gest.eligibility.read}. Deliberadamente não é "sem
     * token": um 401 provaria autenticação, e o que este cenário mede é autorização.
     */
    @Given("the caller holds no eligibility authority")
    public void chamadorSemAutoridadeDeElegibilidade() {
        state.currentToken = TestJwtFactory.buildToken(SharedSteps.TENANT, "gest.tipo-gesto.read");
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("the caller checks eligibility with urgencia {string}")
    public void consultarElegibilidade(String urgencia) {
        estadoAntes = estadoAtualDeTodos();
        transicoesAntes = db.countRows(SharedSteps.SCHEMA, "tipo_gesto_transition");
        http.post(PATH, "{\"urgencyLevel\":\"" + urgencia + "\"}");
    }

    @And("the caller checks eligibility with urgencia {string} again")
    public void consultarElegibilidadeDeNovo(String urgencia) {
        state.previousResponse = state.lastResponse;
        http.post(PATH, "{\"urgencyLevel\":\"" + urgencia + "\"}");
    }

    // ── Then ─────────────────────────────────────────────────────────────────

    @And("{string} is true")
    public void campoBooleanoVerdadeiro(String campo) {
        assertThat(http.lastJson().get(campo).asBoolean())
                .as("corpo: %s", state.lastResponse.getBody())
                .isTrue();
    }

    @And("{string} is false")
    public void campoBooleanoFalso(String campo) {
        assertThat(http.lastJson().get(campo).asBoolean())
                .as("corpo: %s", state.lastResponse.getBody())
                .isFalse();
    }

    @And("missingStates is empty")
    public void missingStatesVazio() {
        assertThat(missingStates()).isEmpty();
    }

    /**
     * {@code missingStates} vem no formato {@code <nome>:<ESTADO>} — asserta-se o par inteiro, e
     * não só o nome: se o serviço passasse a reportar o estado errado, uma asserção só pelo nome
     * continuaria verde.
     */
    @And("missingStates names {string} in state {string}")
    public void missingStatesNomeiaEmEstado(String nome, String estado) {
        assertThat(missingStates()).contains(nome + ":" + estado);
    }

    @And("missingStates does not name {string}")
    public void missingStatesNaoNomeia(String nome) {
        assertThat(missingStates()).noneMatch(s -> s.startsWith(nome + ":"));
    }

    @Then("both responses are 200 with identical {string} value")
    public void ambasRespostas200EIguais(String campo) {
        assertThat(state.previousResponse).as("a primeira chamada não foi feita").isNotNull();
        assertThat(state.previousResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(state.lastResponse.getStatusCode().value()).isEqualTo(200);
        JsonNode primeira = http.parse(state.previousResponse.getBody());
        assertThat(http.lastJson().get(campo)).isEqualTo(primeira.get(campo));
    }

    @And("the detectabilityState of {string} has not changed")
    public void estadoNaoMudou(String nome) {
        assertThat(estadoAtualDeTodos())
                .as("a consulta de elegibilidade é somente leitura")
                .isEqualTo(estadoAntes);
    }

    @And("no transition record was created")
    public void nenhumaTransicaoFoiCriada() {
        assertThat(db.countRows(SharedSteps.SCHEMA, "tipo_gesto_transition"))
                .isEqualTo(transicoesAntes);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private List<String> missingStates() {
        JsonNode nodes = http.lastJson().get("missingStates");
        assertThat(nodes).as("a resposta tem de trazer missingStates").isNotNull();
        List<String> valores = new ArrayList<>();
        nodes.forEach(n -> valores.add(n.asText()));
        return valores;
    }

    /** Fotografia do catálogo do tenant — a base da prova de "nada foi mutado". */
    private String estadoAtualDeTodos() {
        return db.getJdbcTemplate()
                .queryForList(
                        "SELECT nome || '=' || detectability_state FROM "
                                + SharedSteps.SCHEMA
                                + ".tipo_gesto ORDER BY nome",
                        String.class)
                .toString();
    }
}
