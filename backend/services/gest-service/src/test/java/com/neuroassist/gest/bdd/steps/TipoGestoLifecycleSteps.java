package com.neuroassist.gest.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.neuroassist.gest.bdd.BddTestContext;
import com.neuroassist.gest.bdd.GestHttp;
import com.neuroassist.gest.bdd.ScenarioState;
import com.neuroassist.gest.bdd.TestJwtFactory;
import com.neuroassist.gest.infrastructure.persistence.repository.TipoGestoTransitionJpaRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

/**
 * Steps do ciclo de vida de detectabilidade do TipoGesto — {@code tipo-gesto-lifecycle.feature}.
 *
 * <p>Autenticação por Bearer JWT. Antes autenticavam por HTTP Basic contra dois usuários em memória
 * declarados no {@code SecurityConfig} de produção — com senha em texto plano e sem
 * {@code @Profile} — que existiam <i>só</i> para servir esta suíte. O Basic saiu; as authorities
 * daqueles dois usuários viraram os dois conjuntos de {@link TestJwtFactory}, e a distinção entre
 * eles é o que mantém os cenários de 403 com o que medir.
 */
@Slf4j
@RequiredArgsConstructor
public class TipoGestoLifecycleSteps {

    private static final String BASE_PATH = "/gest-service/tipo-gesto";

    private final GestHttp http;
    private final ScenarioState state;
    private final BddTestContext db;
    private final TipoGestoTransitionJpaRepository transitionJpaRepository;

    // ── Background ─────────────────────────────────────────────────────────

    /**
     * Asserção, não declaração. A limpeza acontece no gancho de {@code SharedSteps}; este step
     * verifica que ela de fato aconteceu — um vazamento de dado entre cenários corromperia os
     * cenários de nome duplicado sem dizer por quê.
     */
    @And("no TipoGesto named {string} exists in the tenant")
    public void nenhumTipoGestoComEsseNomeExiste(String nome) {
        assertThat(db.tipoGestoIdByNome(nome, SharedSteps.SCHEMA)).isNull();
    }

    // ── When — Registro ────────────────────────────────────────────────────

    @When("the conductor registers a primitive with nome {string}")
    public void oCondutorRegistraPrimitivoComNome(String nome) {
        registrar(nome);
    }

    @When("the conductor registers a primitive with nome of length 121")
    public void oCondutorRegistraPrimitivoComNomeDe121Caracteres() {
        registrar("A".repeat(121));
    }

    /** Chamador sem {@code gest.tipo-gesto.criar} — o token de leitura já está posto pelo Given. */
    @When("the caller registers a primitive with nome {string}")
    public void oChamadorRegistraPrimitivoComNome(String nome) {
        registrar(nome);
    }

    private void registrar(String nome) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nome", nome);
        ResponseEntity<String> response = http.post(BASE_PATH, http.write(body));
        state.lastEntityId = extrairId(response);
    }

    // ── When — Transições ──────────────────────────────────────────────────

    @When("the conductor transitions {string} to state {string} with reason {string}")
    public void oCondutorTransicionaComRazao(String nome, String estado, String razao) {
        transicionar(state.lastEntityId, estado, razao, null);
    }

    @When("the conductor transitions {string} to state {string} without engineFeatureRef")
    public void oCondutorTransicionaSemEngineFeatureRef(String nome, String estado) {
        transicionar(state.lastEntityId, estado, "motivo do teste", null);
    }

    @When("the conductor transitions {string} to state {string} with engineFeatureRef {string}")
    public void oCondutorTransicionaComEngineFeatureRef(String nome, String estado, String ref) {
        transicionar(state.lastEntityId, estado, "motivo do teste", ref);
    }

    @When("the conductor transitions {string} to state {string} without reason")
    public void oCondutorTransicionaSemRazao(String nome, String estado) {
        transicionar(state.lastEntityId, estado, null, null);
    }

    @When("the conductor transitions {string} to state {string}")
    public void oCondutorTransiciona(String nome, String estado) {
        transicionar(state.lastEntityId, estado, "motivo do teste", null);
    }

    @When("the caller transitions {string} to state {string}")
    public void oChamadorTransiciona(String nome, String estado) {
        transicionar(state.lastEntityId, estado, "motivo do teste", null);
    }

    private ResponseEntity<String> transicionar(
            String id, String toState, String reason, String engineFeatureRef) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("toState", toState);
        body.put("actor", "bdd");
        if (reason != null) {
            body.put("reason", reason);
        }
        if (engineFeatureRef != null) {
            body.put("engineFeatureRef", engineFeatureRef);
        }
        return http.post(BASE_PATH + "/" + id + "/transitions", http.write(body));
    }

    // ── When — Auditoria ───────────────────────────────────────────────────

    @When("the audit trail is queried for {string}")
    public void aTrilhaDeAuditoriaEConsultada(String nome) {
        // Não há endpoint HTTP de auditoria; a trilha é lida do repositório, com o tenant ligado
        // pelo gancho de SharedSteps. As asserções abaixo consultam o mesmo repositório.
        assertThat(state.lastEntityId)
                .as("nenhum TipoGesto foi criado pelo Given deste cenário")
                .isNotNull();
    }

    // ── Then — Corpo da resposta ───────────────────────────────────────────

    @And("the returned TipoGesto has detectabilityState {string}")
    public void oTipoGestoRetornadoTemEstado(String esperado) {
        assertThat(campo("detectabilityState")).isEqualTo(esperado);
    }

    @And("the returned TipoGesto has engineFeatureRef null")
    public void oTipoGestoRetornadoTemEngineFeatureRefNulo() {
        assertThat(campo("engineFeatureRef")).isNull();
    }

    @And("the returned TipoGesto has engineFeatureRef {string}")
    public void oTipoGestoRetornadoTemEngineFeatureRef(String esperado) {
        assertThat(campo("engineFeatureRef")).isEqualTo(esperado);
    }

    /**
     * Estado lido do BANCO, não do corpo da resposta de erro. Um {@code toState} recusado tem de
     * deixar a linha intacta — ler a resposta do erro não provaria isso.
     */
    @And("the detectabilityState of {string} remains {string}")
    public void oEstadoDeContinua(String nome, String esperado) {
        assertThat(db.getStateFromDb(nome)).isEqualTo(esperado);
    }

    // ── Then — Trilha de auditoria ─────────────────────────────────────────

    @And("a transition record exists with fromState null and toState {string}")
    public void existeRegistroDeTransicaoInicial(String toState) {
        assertThat(transicoes())
                .anyMatch(r -> r.getFromState() == null && toState.equals(r.getToState()));
    }

    @And("a new audit record exists for the transition with toState {string}")
    public void existeNovoRegistroDeAuditoria(String toState) {
        assertThat(transicoes()).anyMatch(r -> toState.equals(r.getToState()));
    }

    @Then("exactly {int} transition records exist in chronological order")
    public void existemExatamenteNRegistrosEmOrdem(int esperado) {
        var registros = transicoes();
        assertThat(registros).hasSize(esperado);
        assertThat(registros)
                .as("a trilha é append-only e tem de sair em ordem cronológica")
                .isSortedAccordingTo(java.util.Comparator.comparing(r -> r.getAt()));
    }

    /**
     * Append-only (ADR-006, invariante #4): reler a trilha depois de uma tentativa de escrita
     * ilegal tem de devolver exatamente os mesmos ids, na mesma ordem, com os mesmos carimbos.
     */
    @And("no transition record has been modified")
    public void nenhumRegistroFoiModificado() {
        var antes =
                transicoes().stream()
                        .map(r -> r.getId() + "|" + r.getToState() + "|" + r.getAt())
                        .toList();
        // Uma regressão ilegal a partir de supported: tem de ser recusada e não pode reescrever
        // nada da trilha já gravada.
        transicionar(state.lastEntityId, "registered", "tentativa ilegal", null);
        var depois =
                transicoes().stream()
                        .map(r -> r.getId() + "|" + r.getToState() + "|" + r.getAt())
                        .toList();
        assertThat(depois).isEqualTo(antes);
    }

    // ── Given — Montagem de estado, pelo caminho real ──────────────────────

    @Given("a TipoGesto {string} exists in state \"registered\"")
    public void tipoGestoExisteEmRegistered(String nome) {
        registrar(nome);
        exigir201();
    }

    @Given("a TipoGesto {string} exists in state \"pending_training\"")
    public void tipoGestoExisteEmPendingTraining(String nome) {
        registrar(nome);
        exigir201();
        exigirOk(transicionar(state.lastEntityId, "pending_training", "setup", null));
    }

    @Given("a TipoGesto {string} exists in state \"pending_training\" with engineFeatureRef null")
    public void tipoGestoExisteEmPendingTrainingSemRef(String nome) {
        tipoGestoExisteEmPendingTraining(nome);
        assertThat(db.tipoGestoIdByNome(nome, SharedSteps.SCHEMA)).isNotNull();
    }

    @Given("a TipoGesto {string} exists in state \"supported\"")
    public void tipoGestoExisteEmSupported(String nome) {
        tipoGestoExisteEmSupportedComRef(nome, "mp.test.v1");
    }

    @Given("a TipoGesto {string} exists in state \"supported\" with engineFeatureRef {string}")
    public void tipoGestoExisteEmSupportedComRef(String nome, String ref) {
        tipoGestoExisteEmPendingTraining(nome);
        exigirOk(transicionar(state.lastEntityId, "supported", "setup", ref));
    }

    @Given("a TipoGesto {string} exists in state \"deprecated\"")
    public void tipoGestoExisteEmDeprecated(String nome) {
        tipoGestoExisteEmPendingTraining(nome);
        exigirOk(transicionar(state.lastEntityId, "deprecated", "setup — depreciado", null));
    }

    @Given("a TipoGesto {string} has gone through transitions {string} -> {string} -> {string}")
    public void tipoGestoPercorreuTransicoes(String nome, String a, String b, String c) {
        registrar(nome);
        exigir201();
        exigirOk(transicionar(state.lastEntityId, b, "setup — passo 1", null));
        exigirOk(transicionar(state.lastEntityId, c, "setup — passo 2", "mp.test.v1"));
    }

    // ── Given — Papéis ─────────────────────────────────────────────────────

    @Given("the caller has role \"gest.read\" only")
    public void oChamadorTemApenasPapelDeLeitura() {
        state.currentToken = TestJwtFactory.buildReadToken(SharedSteps.TENANT);
    }

    // ── Auxiliares ─────────────────────────────────────────────────────────

    private java.util.List<
                    com.neuroassist.gest.infrastructure.persistence.entity
                            .TipoGestoTransitionJpaEntity>
            transicoes() {
        assertThat(state.lastEntityId).isNotNull();
        return transitionJpaRepository.findByTipoGestoId(UUID.fromString(state.lastEntityId));
    }

    private void exigir201() {
        assertThat(state.lastResponse.getStatusCode().value())
                .as("setup falhou ao registrar: %s", state.lastResponse.getBody())
                .isEqualTo(201);
    }

    private void exigirOk(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value())
                .as("setup falhou ao transicionar: %s", response.getBody())
                .isEqualTo(200);
    }

    private String extrairId(ResponseEntity<String> response) {
        if (response.getBody() == null || response.getBody().isBlank()) {
            return null;
        }
        JsonNode id = http.parse(response.getBody()).get("id");
        return id == null || id.isNull() ? null : id.asText();
    }

    private String campo(String nome) {
        JsonNode valor = http.lastJson().get(nome);
        return valor == null || valor.isNull() ? null : valor.asText();
    }
}
