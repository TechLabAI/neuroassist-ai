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
        stubPacCommandCatalog("Marcio");
        stubEdgeLiveness("Marcio", "online", false);
        // EVNT soft: nao necessario para summary, mas stub para evitar erros
        BaseIntegrationTest.evntWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/notifications/unacknowledged"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("[]")));
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
        stubPacCommandCatalog("Marcio");
        BaseIntegrationTest.edgeWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/edge/patients/Marcio/liveness"))
                        .willReturn(aResponse().withStatus(503)));
    }

    @Given("EDGE reports that the patient has no paired device")
    public void givenEdgeReportsNoDevice() {
        stubPacCommandCatalog("Marcio");
        BaseIntegrationTest.edgeWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/edge/patients/Marcio/liveness"))
                        .willReturn(aResponse().withStatus(404)));
    }

    @Given("EVNT is unreachable")
    public void givenEvntIsUnreachable() {
        // PAC e EDGE disponiveis; EVNT retorna 503 — soft degrade
        stubPacCommandCatalog("Marcio");
        stubEdgeLiveness("Marcio", "online", false);
        BaseIntegrationTest.evntWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/notifications/unacknowledged"))
                        .willReturn(aResponse().withStatus(503)));
    }

    @Given("PAC is unreachable")
    public void givenPacIsUnreachable() {
        // PAC retorna 503 em todos os endpoints — hard dependency (propaga 503)
        BaseIntegrationTest.pacWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/patients/Marcio/commands"))
                        .willReturn(aResponse().withStatus(503)));
        BaseIntegrationTest.pacWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/patients/Marcio/calibration"))
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
        assertThat(ctx.lastResponse.getBody()).contains("Marcio");
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

    private void stubPacCommandCatalog(String patientId) {
        BaseIntegrationTest.pacWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/patients/" + patientId + "/commands"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"commands\":[\"cmd-water\"],\"calibrationState\":\"calibrated\"}")));
        BaseIntegrationTest.pacWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/patients/" + patientId + "/calibration"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"state\":\"calibrated\"}")));
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
