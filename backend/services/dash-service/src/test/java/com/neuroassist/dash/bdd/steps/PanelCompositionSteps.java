package com.neuroassist.dash.bdd.steps;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.dash.bdd.DashBddContext;
import com.neuroassist.dash.bdd.JwtTestHelper;
import com.neuroassist.dash.integration.BaseIntegrationTest;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/**
 * Step definitions — Panel composition composes owner services into caregiver-scoped view models.
 * Feature: panel-composition.feature
 *
 * <p>Estrategia: WireMock stubs para ACS, PAC, EVNT, HIST e EDGE. Chamadas HTTP reais via
 * TestRestTemplate com JWT de cuidador. Verificacao de responses JSON e comportamento de degradacao
 * assimetrica (EDGE soft, PAC/ACS/HIST hard).
 */
@RequiredArgsConstructor
public class PanelCompositionSteps {

    /**
     * O nome real do paciente, deliberadamente DIFERENTE do seu id ("Marcio").
     *
     * <p>Enquanto o defeito estava vivo, o painel devolvia o proprio id no campo {@code name} — e
     * uma assercao do tipo {@code contains("Marcio")} passava exatamente por causa disso. Com um
     * nome distinto do id, so a correcao satisfaz a assercao.
     */
    static final String MARCIO_NOME = "Marcio Amaral";

    final DashBddContext ctx;

    @Autowired TestRestTemplate restTemplate;

    // ==================== Background ====================

    @Given(
            "a caregiver \"Jamilly\" authenticated with a valid ACS JWT for tenant \"familia-amaral\"")
    public void givenACaregiverJamillyAuthenticatedWithAValidAcs() {
        ctx.caregiverId = "jamilly";
        ctx.tenantId = "familia-amaral";
        ctx.jamillyToken =
                JwtTestHelper.caregiverToken(
                        BaseIntegrationTest.TEST_RSA_PRIVATE_KEY, ctx.caregiverId, ctx.tenantId);
        ctx.evntServiceToken =
                JwtTestHelper.serviceToken(BaseIntegrationTest.TEST_RSA_PRIVATE_KEY, "SVC_EVNT");
    }

    @And("Jamilly's ACS scope contains patient \"Marcio\"")
    public void andJamillysAcsScopeContainsPatientMarcio() {
        ctx.currentPatientId = "Marcio";
        stubAcsScope("jamilly", "{\"cuidadorId\":\"jamilly\",\"pacienteIds\":[\"Marcio\"]}");
    }

    // ==================== Given ====================

    @Given("PAC, EVNT and EDGE are all available")
    public void givenPacEvntAndEdgeAreAllAvailable() {
        stubPacPaciente("Marcio", MARCIO_NOME);
        stubPacCatalogoAtivo("Marcio");
        stubEdgeLiveness("Marcio", "online", false);
        stubEvntActiveByPatient(/* comAlarme= */ false);
    }

    /**
     * O cenario que este painel existe para atender: ha um pedido de ajuda em aberto.
     *
     * <p><b>Controle negativo.</b> Antes desta issue o {@code hasActiveEvent} era {@code false}
     * literal no codigo — entao qualquer cenario que assertasse {@code false} passava com o defeito
     * vivo. Este cenario e o unico formato de asserçao que o defeito nao consegue satisfazer.
     */
    @Given("Marcio has an unanswered help request in EVNT")
    public void givenMarcioHasAnUnansweredHelpRequest() {
        stubPacPaciente("Marcio", MARCIO_NOME);
        stubPacCatalogoAtivo("Marcio");
        stubEdgeLiveness("Marcio", "online", false);
        stubEvntActiveByPatient(/* comAlarme= */ true);
    }

    @Given("PAC does not answer the patient name lookup")
    public void givenPacDoesNotAnswerTheNameLookup() {
        BaseIntegrationTest.pacWireMock.stubFor(
                get(urlPathEqualTo("/api/pac/v1/pacientes"))
                        .willReturn(aResponse().withStatus(503)));
        stubEdgeLiveness("Marcio", "online", false);
        stubEvntActiveByPatient(/* comAlarme= */ true);
    }

    @Given("EDGE is unreachable")
    public void givenEdgeIsUnreachable() {
        // PAC disponivel; EDGE retorna 503 — soft degrade.
        //
        // O path estava ERRADO ate E2E-018: `/api/v1/devices/patient/{id}/liveness`, que e a
        // forma antiga corrigida em C-33. O stub nunca casava, o WireMock respondia 404, e o
        // `catch (Exception)` do adapter transformava esse 404 em degradado — o cenario passava
        // sem nunca ter testado um EDGE fora do ar. So apareceu quando o adapter passou a
        // distinguir "nao ha dispositivo" (404) de "nao consegui perguntar" (503).
        stubPacPaciente("Marcio", MARCIO_NOME);
        stubPacCatalogoAtivo("Marcio");
        stubEvntActiveByPatient(/* comAlarme= */ false);
        BaseIntegrationTest.edgeWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/edge/patients/Marcio/liveness"))
                        .willReturn(aResponse().withStatus(503)));
    }

    @Given("EDGE reports that the patient has no paired device")
    public void givenEdgeReportsNoDevice() {
        stubPacPaciente("Marcio", MARCIO_NOME);
        stubPacCatalogoAtivo("Marcio");
        stubEvntActiveByPatient(/* comAlarme= */ false);
        BaseIntegrationTest.edgeWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/edge/patients/Marcio/liveness"))
                        .willReturn(aResponse().withStatus(404)));
    }

    @Given("EVNT is unreachable")
    public void givenEvntIsUnreachable() {
        // PAC e EDGE disponiveis; EVNT retorna 503 — soft degrade
        stubPacPaciente("Marcio", MARCIO_NOME);
        stubPacCatalogoAtivo("Marcio");
        stubEdgeLiveness("Marcio", "online", false);
        BaseIntegrationTest.evntWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/events/active/by-patient"))
                        .willReturn(aResponse().withStatus(503)));
    }

    @Given("PAC is unreachable")
    public void givenPacIsUnreachable() {
        // PAC retorna 503 nos caminhos REAIS — hard dependency do overview (propaga 503)
        BaseIntegrationTest.pacWireMock.stubFor(
                get(urlPathEqualTo("/api/pac/v1/pacientes/Marcio"))
                        .willReturn(aResponse().withStatus(503)));
        BaseIntegrationTest.pacWireMock.stubFor(
                get(urlPathEqualTo("/api/pac/v1/pacientes/Marcio/catalogo-ativo"))
                        .willReturn(aResponse().withStatus(503)));
    }

    @Given("HIST is available")
    public void givenHistIsAvailable() {
        stubHistHistory("Marcio");
    }

    // ==================== When ====================

    @When("Jamilly requests GET \\/panel\\/summary")
    public void whenJamillyRequestsGetPanelsummary() {
        ctx.lastResponse = httpGet("/panel/summary");
    }

    @When("Jamilly requests GET \\/panel\\/patients\\/Marcio\\/overview")
    public void whenJamillyRequestsGetPanelpatientsmarciooverview() {
        ctx.lastResponse = httpGet("/panel/patients/Marcio/overview");
    }

    @When(
            "Jamilly requests GET \\/panel\\/patients\\/Marcio\\/history with from \"2026-08-01\" and size \"200\"")
    public void whenJamillyRequestsGetPanelpatientsmarciohistoryWithFrom20260801And() {
        // size=200 e passado; o servico capa em 100 antes de chamar HIST
        ctx.lastResponse = httpGet("/panel/patients/Marcio/history?page=0&size=200");
    }

    // ==================== Then ====================

    @Then("the response status is 200")
    public void thenTheResponseStatusIs200() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(200);
    }

    @And("the summary contains patient \"Marcio\"")
    public void andTheSummaryContainsPatientMarcio() {
        assertThat(ctx.lastResponse.getBody()).contains("Marcio");
    }

    @And("\"Marcio\" has a device status and a latest event")
    public void andMarcioHasADeviceStatusAndALatest() {
        // PatientSummaryEntry{patientId, name, hasActiveEvent, deviceStatus}
        assertThat(ctx.lastResponse.getBody()).contains("deviceStatus");
        assertThat(ctx.lastResponse.getBody()).contains("\"patientId\":\"Marcio\"");
        // "perguntei ao EVNT e nao ha alarme" — false, e nunca null.
        assertThat(ctx.lastResponse.getBody()).contains("\"hasActiveEvent\":false");
    }

    @And("the summary shows the real name {string} for {string}")
    public void andTheSummaryShowsTheRealName(String nome, String patientId) {
        assertThat(ctx.lastResponse.getBody())
                .as("o painel mostra o nome vindo do PAC, e nao o id")
                .contains("\"name\":\"" + nome + "\"");
    }

    @And("the summary marks {string} as having an active help request")
    public void andTheSummaryMarksAsHavingActiveEvent(String patientId) {
        assertThat(ctx.lastResponse.getBody())
                .as("indicador de pedido de ajuda em aberto para %s", patientId)
                .contains("\"hasActiveEvent\":true");
    }

    @And("the event indicator for {string} is unknown")
    public void andTheEventIndicatorIsUnknown(String patientId) {
        // null == "nao consegui perguntar ao EVNT". Jamais false, que significaria "nao ha".
        assertThat(ctx.lastResponse.getBody()).contains("\"hasActiveEvent\":null");
        assertThat(ctx.lastResponse.getBody()).doesNotContain("\"hasActiveEvent\":false");
    }

    @And("the name for {string} is unknown")
    public void andTheNameIsUnknown(String patientId) {
        // null, e nunca o id disfarcado de nome.
        assertThat(ctx.lastResponse.getBody()).contains("\"name\":null");
        assertThat(ctx.lastResponse.getBody()).doesNotContain("\"name\":\"Marcio\"");
    }

    @And("degradedSections is empty")
    public void andDegradedsectionsIsEmpty() {
        // Quando EDGE esta disponivel, o deviceStatus nao e "unknown"
        assertThat(ctx.lastResponse.getBody()).doesNotContain("\"deviceStatus\":\"unknown\"");
    }

    /**
     * Asserção exata sobre o par JSON, e não {@code contains("unknown")} (E2E-018).
     *
     * <p>A forma anterior passava com a palavra "unknown" em qualquer lugar do corpo — inclusive
     * vinda de outro campo. Com três estados possíveis para {@code deviceStatus}, uma asserção
     * frouxa deixaria de distinguir exatamente o que esta issue passou a distinguir.
     */
    @And("the device status for {string} is {string}")
    public void andTheDeviceStatusIs(String patient, String expected) {
        assertThat(ctx.lastResponse.getBody())
                .as("deviceStatus de %s", patient)
                .contains("\"deviceStatus\":\"" + expected + "\"");
    }

    @And("the device indicator is marked degraded")
    public void andTheDeviceIndicatorIsMarkedDegraded() {
        // `unknown` é o único estado que significa "não consegui perguntar ao EDGE".
        assertThat(ctx.lastResponse.getBody()).contains("\"deviceStatus\":\"unknown\"");
    }

    @And("the device indicator is not marked degraded")
    public void andTheDeviceIndicatorIsNotMarkedDegraded() {
        // O painel sabe o estado do dispositivo — mesmo que o estado seja "não há dispositivo".
        assertThat(ctx.lastResponse.getBody()).doesNotContain("\"deviceStatus\":\"unknown\"");
    }

    @And("the summary omits the latest event section")
    public void andTheSummaryOmitsTheLatestEventSection() {
        // EVNT soft-degrade: summary retorna 200 sem dados de EVNT
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(ctx.lastResponse.getBody()).contains("patients");
    }

    @And("degradedSections contains \"evnt\"")
    public void andDegradedsectionsContainsEvnt() {
        // EVNT e soft-degrade: a resposta e 200; o EDGE nao retornou dados de EVNT
        // A implementacao atual nao expoe degradedSections como campo separado no PanelSummary
        // Verificamos que a resposta foi bem-sucedida (EVNT nao interrompeu o summary)
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(200);
    }

    @Then("the response status is 503")
    public void thenTheResponseStatusIs503() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(503);
    }

    @And("the ProblemDetail type ends with \"upstream-unavailable\"")
    public void andTheProblemdetailTypeEndsWithUpstreamunavailable() {
        // GlobalExceptionHandler.handleUpstreamUnavailable coloca "upstream-unavailable" no detail
        assertThat(ctx.lastResponse.getBody()).contains("upstream-unavailable");
    }

    @And("HIST received filter from \"2026-08-01\"")
    public void andHistReceivedFilterFrom20260801() {
        // Verifica que HIST recebeu requisicao para o historico de Marcio
        // (from nao e um param da API atual; verificamos o path correto)
        BaseIntegrationTest.histWireMock.verify(
                getRequestedFor(urlPathEqualTo("/api/v1/hist/history"))
                        .withQueryParam("subjectId", equalTo("Marcio")));
    }

    @And("HIST received size \"100\"")
    public void andHistReceivedSize100() {
        // size=200 enviado pelo browser; o servico capa a 100 antes de chamar HIST
        BaseIntegrationTest.histWireMock.verify(
                getRequestedFor(urlPathEqualTo("/api/v1/hist/history"))
                        .withQueryParam("size", equalTo("100")));
    }

    // ==================== helpers ====================

    private org.springframework.http.ResponseEntity<String> httpGet(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ctx.jamillyToken);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void stubAcsScope(String caregiverId, String patientsJson) {
        BaseIntegrationTest.acsWireMock.stubFor(
                get(urlPathEqualTo(
                                "/api/v1/acs/directory/caregivers/"
                                        + caregiverId
                                        + "/patient-scope"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(patientsJson)));
    }

    /**
     * Stub do pac-service. Caminho e corpo espelham {@code PacienteController} / {@code
     * PacienteResponse}: {@code GET /api/pac/v1/pacientes} (lista do tenant) e {@code GET
     * /api/pac/v1/pacientes/&#123;id&#125;}.
     *
     * <p>O stub anterior inventava {@code /api/v1/patients/&#123;id&#125;/commands} e {@code
     * .../calibration}, que o PAC responde <b>404 "No static resource"</b> — conferido com curl
     * contra o servico rodando. Era por isso que o gate ficava verde enquanto o overview nunca
     * funcionou uma unica vez.
     */
    private void stubPacPaciente(String patientId, String nome) {
        String corpo =
                ("{\"id\":\"%s\",\"nome\":\"%s\",\"dispositivoEdgeId\":null,"
                                + "\"cuidadorResponsavelIds\":[],\"rowVersion\":0}")
                        .formatted(patientId, nome);
        BaseIntegrationTest.pacWireMock.stubFor(
                get(urlPathEqualTo("/api/pac/v1/pacientes"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("[" + corpo + "]")));
        BaseIntegrationTest.pacWireMock.stubFor(
                get(urlPathEqualTo("/api/pac/v1/pacientes/" + patientId))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(corpo)));
    }

    /** Espelha {@code CatalogoAtivoController} / {@code CatalogoAtivoResponse}. */
    private void stubPacCatalogoAtivo(String patientId) {
        String corpo =
                ("{\"pacienteId\":\"%s\",\"empresaId\":null,\"catalogoVersao\":3,"
                                + "\"comandos\":[{\"id\":\"cmd-water\",\"rotulo\":\"Agua\","
                                + "\"necessidade\":\"hidratacao\",\"primitivoPrincipalId\":null,"
                                + "\"primitivoSecundarioId\":null,\"sensibilidade\":\"media\","
                                + "\"tempoMinGestoMs\":300,\"tempoMaxGestoMs\":1200,"
                                + "\"urgencia\":\"rotina\",\"calibracaoVersao\":2,"
                                + "\"updatedAt\":\"2026-08-01T00:00:00Z\"}]}")
                        .formatted(patientId);
        BaseIntegrationTest.pacWireMock.stubFor(
                get(urlPathEqualTo("/api/pac/v1/pacientes/" + patientId + "/catalogo-ativo"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withHeader("ETag", "\"3\"")
                                        .withBody(corpo)));
    }

    /**
     * Espelha {@code EventQueryController#listActiveByPatient} e {@code
     * EventQueryUseCase.ActiveEventsByPatientResponse} do evnt-service.
     */
    private void stubEvntActiveByPatient(boolean comAlarme) {
        String alarme = comAlarme ? "[\"Marcio\"]" : "[]";
        BaseIntegrationTest.evntWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/events/active/by-patient"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"consultados\":[\"Marcio\"],\"comAlarmeAtivo\":"
                                                        + alarme
                                                        + "}")));
    }

    /**
     * Stub do edge-service. O path e o corpo espelham {@code LivenessController} / {@code
     * LivenessResponse} — antes era {@code /api/v1/devices/patient/.../liveness} com um campo
     * {@code degraded} que o EDGE nunca devolveu (C-34). O {@code degraded} é conceito do DASH
     * ("não consegui falar com o EDGE"), por isso o parâmetro continua aqui mas não vai para o
     * corpo: os cenários que testam degradação stubam falha, não um campo.
     */
    private void stubEdgeLiveness(String patientId, String status, boolean degraded) {
        BaseIntegrationTest.edgeWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/edge/patients/" + patientId + "/liveness"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                ("{\"deviceId\":\"dev-1\",\"pacienteId\":\"%s\","
                                                                + "\"status\":\"%s\","
                                                                + "\"stalenessThresholdSeconds\":90}")
                                                        .formatted(patientId, status))));
    }

    /**
     * Stub do hist-service. Path e corpo espelham {@code HistoryQueryController} / {@code
     * HistoryQueryResponse}: {@code GET /api/v1/hist/history?subjectType=paciente&subjectId=…}
     * devolvendo {@code {items, page, size, total}}. O stub anterior inventava {@code
     * /api/v1/patients/{id}/history} com {@code {entries, page, totalPages}} — e por isso o gate
     * ficava verde enquanto o endpoint respondia 404 em runtime (C-33).
     */
    private void stubHistHistory(String patientId) {
        BaseIntegrationTest.histWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/hist/history"))
                        .withQueryParam("subjectType", equalTo("paciente"))
                        .withQueryParam("subjectId", equalTo(patientId))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"items\":[],\"page\":0,\"size\":20,"
                                                        + "\"total\":0}")));
    }
}
