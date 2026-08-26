package com.neuroassist.gest.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.neuroassist.gest.bdd.BddTestContext;
import com.neuroassist.gest.bdd.GestHttp;
import com.neuroassist.gest.bdd.ScenarioState;
import com.neuroassist.gest.bdd.TestJwtFactory;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Steps da validação de relatório de reconhecimento — {@code report-validation.feature}.
 *
 * <p>A invariante central é negativa: o GEST valida e <b>não guarda</b>. Uma asserção negativa só
 * vale se conseguir falhar, então "nada foi gravado" aqui é contagem de linhas de todas as tabelas
 * do GEST antes e depois da chamada — inclusive {@code processed_events}, que é onde uma
 * deduplicação por {@code reportId} escreveria se alguém a acrescentasse sem revisar a invariante.
 */
@Slf4j
@RequiredArgsConstructor
public class ReportValidationSteps {

    private static final String PATH = "/gest-service/detection-contract";

    /** Toda tabela do GEST no schema do tenant — a lista é a definição de "nada foi gravado". */
    private static final List<String> TABELAS_GEST =
            List.of(
                    "tipo_gesto",
                    "tipo_gesto_transition",
                    "detection_profile",
                    "detection_profile_primitive",
                    "processed_events");

    private final GestHttp http;
    private final ScenarioState state;
    private final BddTestContext db;

    private Map<String, Integer> linhasAntes;
    private String ultimoCorpoEnviado;

    // ── Given ────────────────────────────────────────────────────────────────

    @Given("the GEST tables are empty")
    public void tabelasDoGestVazias() {
        linhasAntes = contarLinhas();
        assertThat(linhasAntes.values()).allMatch(n -> n == 0);
    }

    @Given("the caller holds only {string}")
    public void chamadorComApenasAAutoridade(String autoridade) {
        state.currentToken = TestJwtFactory.buildToken(SharedSteps.TENANT, autoridade);
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("the edge posts a report:")
    public void bordaPostaRelatorio(String reportJson) {
        postarRelatorio(reportJson);
    }

    @And("the edge posts the same report again")
    public void bordaPostaOMesmoRelatorioDeNovo() {
        assertThat(ultimoCorpoEnviado).as("nenhum relatório foi enviado antes").isNotNull();
        state.previousResponse = state.lastResponse;
        http.post(PATH, ultimoCorpoEnviado);
    }

    @When("the edge posts a report carrying the forbidden field {string}")
    public void bordaPostaRelatorioComCampoProibido(String campo) {
        postarComCampoProibido(campo, "conteudo-qualquer");
    }

    @When("the edge posts a report carrying the forbidden field {string} with content {string}")
    public void bordaPostaRelatorioComCampoProibidoEConteudo(String campo, String conteudo) {
        postarComCampoProibido(campo, conteudo);
    }

    @When("the caller gets the detection contract descriptor")
    public void obterDescritorDoContrato() {
        http.get(PATH);
    }

    // ── Then ─────────────────────────────────────────────────────────────────

    @And("the response body has valid true")
    public void corpoTemValidTrue() {
        assertThat(http.lastJson().get("valid").asBoolean())
                .as("corpo: %s", state.lastResponse.getBody())
                .isTrue();
    }

    @And("the response body has valid false")
    public void corpoTemValidFalse() {
        assertThat(http.lastJson().get("valid").asBoolean())
                .as("corpo: %s", state.lastResponse.getBody())
                .isFalse();
    }

    @And("the response body reports no errors")
    public void corpoSemErros() {
        assertThat(erros()).isEmpty();
    }

    /** O campo tem de ser nomeado — um erro genérico não diz à borda o que corrigir. */
    @And("the errors list names the field {string}")
    public void listaDeErrosNomeiaOCampo(String campo) {
        assertThat(erros()).as("erros: %s", erros()).anyMatch(e -> e.contains("'" + campo + "'"));
    }

    @And("the response body does not contain {string}")
    public void corpoNaoContem(String sentinela) {
        assertThat(state.lastResponse.getBody())
                .as("LGPD: o corpo de erro nomeia o campo proibido, nunca o conteúdo dele")
                .doesNotContain(sentinela);
    }

    @And("no row was written to any GEST table")
    public void nenhumaLinhaFoiGravada() {
        assertThat(linhasAntes).as("o Given não fotografou as contagens").isNotNull();
        assertThat(contarLinhas())
                .as("a validação é stateless — nada do relatório pode chegar ao banco")
                .isEqualTo(linhasAntes);
    }

    @And("both responses carry the same verdict")
    public void ambasRespostasComOMesmoVeredito() {
        assertThat(state.previousResponse).isNotNull();
        JsonNode primeira = http.parse(state.previousResponse.getBody());
        assertThat(http.lastJson().get("valid")).isEqualTo(primeira.get("valid"));
        assertThat(http.lastJson().get("errors")).isEqualTo(primeira.get("errors"));
    }

    // ── Then — descritor ─────────────────────────────────────────────────────

    @And("the response body has version {string}")
    public void corpoTemVersion(String esperado) {
        assertThat(http.lastJson().get("version").asText()).isEqualTo(esperado);
    }

    @And("the descriptor requires the fields {string}")
    public void descritorExigeOsCampos(String lista) {
        assertThat(camposDo("requiredFields")).containsExactlyInAnyOrderElementsOf(separar(lista));
    }

    @And("the descriptor forbids the fields {string}")
    public void descritorProibeOsCampos(String lista) {
        assertThat(camposDo("forbiddenFields")).containsExactlyInAnyOrderElementsOf(separar(lista));
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private void postarRelatorio(String reportJson) {
        // O contrato do endpoint embrulha o relatório: DetectionContractRequest(Map report).
        ultimoCorpoEnviado = "{\"report\":" + reportJson + "}";
        http.post(PATH, ultimoCorpoEnviado);
    }

    private void postarComCampoProibido(String campo, String conteudo) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("gestureId", "3f1c-aaa");
        report.put("confidence", 0.91);
        report.put("windowMs", 700);
        report.put(campo, conteudo);
        postarRelatorio(http.write(report));
    }

    private List<String> erros() {
        JsonNode nodes = http.lastJson().get("errors");
        assertThat(nodes).as("a resposta tem de trazer a lista de erros").isNotNull();
        List<String> valores = new ArrayList<>();
        nodes.forEach(n -> valores.add(n.asText()));
        return valores;
    }

    private List<String> camposDo(String propriedade) {
        JsonNode nodes = http.lastJson().get(propriedade);
        assertThat(nodes).as("descritor sem '%s'", propriedade).isNotNull();
        List<String> valores = new ArrayList<>();
        nodes.forEach(n -> valores.add(n.asText()));
        return valores;
    }

    private List<String> separar(String lista) {
        return java.util.Arrays.stream(lista.split(",")).map(String::trim).toList();
    }

    private Map<String, Integer> contarLinhas() {
        Map<String, Integer> contagens = new LinkedHashMap<>();
        for (String tabela : TABELAS_GEST) {
            contagens.put(tabela, db.countRows(SharedSteps.SCHEMA, tabela));
        }
        return contagens;
    }
}
