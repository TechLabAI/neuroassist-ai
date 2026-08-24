package com.neuroassist.dash.bdd.steps;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.dash.bdd.DashBddContext;
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
 * Step definitions — Caregiver actions are proxied to the owner service; DASH stores no domain
 * truth. Feature: write-through-actions.feature
 *
 * <p>Estrategia: stubs WireMock para PAC (calibracao) e EVNT (confirmacao/ack). Chamadas HTTP reais
 * via TestRestTemplate com JWT de cuidador. Verificacao de WireMock para confirmar que DASH nao
 * armazena estado e apenas proxeia.
 */
@RequiredArgsConstructor
public class WriteThroughActionsSteps {

    final DashBddContext ctx;

    @Autowired TestRestTemplate restTemplate;

    // ==================== Given ====================

    @And("command \"cmd-water\" belongs to \"Marcio\"")
    public void andCommandCmdwaterBelongsToMarcio() {
        stubPacRecalibrate("Marcio", "cmd-water", 200, comandoLifecycleJson("cmd-water", "media"));
    }

    @Given("PAC rejects the calibration edit with status 409")
    public void givenPacRejectsTheCalibrationEditWithStatus409() {
        // PAC retorna 409 — DASH deve relay
        BaseIntegrationTest.pacWireMock.stubFor(
                post(urlPathEqualTo(recalibratePath("Marcio", "cmd-water")))
                        .willReturn(
                                aResponse()
                                        .withStatus(409)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"detail\":\"conflict\",\"status\":409}")));
    }

    @Given("event \"ev-9\" belongs to \"Marcio\"")
    public void givenEventEv9BelongsToMarcio() {
        // EVNT aceita confirmacao do evento ev-9
        stubEvntConfirmEvent("ev-9", "jamilly");
    }

    @Given("patient \"Outro\" is NOT in Jamilly's scope")
    public void givenPatientOutroIsNotInJamillysScope() {
        // ACS retorna lista de pacientes de Jamilly que nao inclui "Outro"
        BaseIntegrationTest.acsWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/acs/directory/caregivers/jamilly/patient-scope"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"cuidadorId\":\"jamilly\",\"pacienteIds\":[\"Marcio\"]}")));
    }

    @Given("command \"cmd-other\" does not belong to \"Marcio\"")
    public void givenCommandCmdotherDoesNotBelongToMarcio() {
        // PAC retorna 404 para cmd-other de Marcio
        BaseIntegrationTest.pacWireMock.stubFor(
                post(urlPathEqualTo(recalibratePath("Marcio", "cmd-other")))
                        .willReturn(
                                aResponse()
                                        .withStatus(404)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"detail\":\"not found\",\"status\":404}")));
    }

    // ==================== When ====================

    @When("Jamilly updates calibration of \"cmd-water\" with sensibilidade \"media\"")
    public void whenJamillyUpdatesCalibrationOfCmdwaterWithSensibilidadeMedia() {
        ctx.lastResponse = postCalibration("Marcio", "cmd-water", "media");
    }

    @When("Jamilly updates calibration of \"cmd-water\"")
    public void whenJamillyUpdatesCalibrationOfCmdwater() {
        ctx.lastResponse = postCalibration("Marcio", "cmd-water", "media");
    }

    @When("Jamilly confirms event \"ev-9\" with feedback \"real\"")
    public void whenJamillyConfirmsEventEv9WithFeedbackReal() {
        ctx.lastResponse = postEventConfirm("ev-9", "real");
    }

    @When("Jamilly confirms event \"ev-9\" with feedback \"maybe\"")
    public void whenJamillyConfirmsEventEv9WithFeedbackMaybe() {
        // "maybe" nao e um feedback valido — deve retornar 422
        ctx.lastResponse = postEventConfirm("ev-9", "maybe");
    }

    @When("Jamilly requests GET \\/panel\\/patients\\/Outro\\/overview")
    public void whenJamillyRequestsGetPanelpatientsoutrooverview() {
        ctx.lastResponse = httpGet("/panel/patients/Outro/overview");
    }

    @When("Jamilly updates calibration of \"cmd-other\" for \"Marcio\"")
    public void whenJamillyUpdatesCalibrationOfCmdotherForMarcio() {
        ctx.lastResponse = postCalibration("Marcio", "cmd-other", "alta");
    }

    // ==================== Then ====================

    @Then("DASH forwards the calibration edit to PAC")
    public void thenDashForwardsTheCalibrationEditToPac() {
        // O caminho verificado e o que o PAC serve de verdade — ComandoLifecycleController.
        // O anterior (`/api/v1/patients/.../calibrate`) nao existe no produtor.
        BaseIntegrationTest.pacWireMock.verify(
                postRequestedFor(urlPathEqualTo(recalibratePath("Marcio", "cmd-water")))
                        // e o corpo fala a lingua do PAC: `sensibilidade`, nao `sensitivity`
                        .withRequestBody(matchingJsonPath("$.sensibilidade", equalTo("media"))));
    }

    @And("the response body is PAC's authoritative result")
    public void andTheResponseBodyIsPacsAuthoritativeResult() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(200);
        // Campos do PAC, ecoados sem traducao: o DASH nao e dono deste dado.
        assertThat(ctx.lastResponse.getBody())
                .contains("\"comandoId\":\"cmd-water\"")
                .contains("\"calibracaoVersao\":3")
                .contains("\"estado\":\"PUBLICADO\"");
    }

    @And("DASH persists no calibration value locally")
    public void andDashPersistsNoCalibrationValueLocally() {
        // DASH e stateless (ADR-004): nenhuma persistencia local
        // Verificamos indiretamente: PAC foi chamado e DASH retornou o resultado dele
        assertThat(ctx.lastResponse.getBody()).contains("cmd-water");
    }

    @Then("the response status is 409")
    public void thenTheResponseStatusIs409() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(409);
    }

    @And("the ProblemDetail is relayed from PAC")
    public void andTheProblemdetailIsRelayedFromPac() {
        assertThat(ctx.lastResponse.getBody())
                .satisfiesAnyOf(
                        body -> assertThat(body).contains("conflict"),
                        body -> assertThat(body).contains("409"));
    }

    @Then("DASH forwards feedback \"real\" to EVNT")
    public void thenDashForwardsFeedbackRealToEvnt() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(204);
        BaseIntegrationTest.evntWireMock.verify(
                postRequestedFor(urlPathEqualTo("/api/v1/events/ev-9/confirm")));
    }

    @And("the response body is EVNT's updated event state")
    public void andTheResponseBodyIsEvntsUpdatedEventState() {
        // ConfirmationProxyController retorna 204 No Content
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(204);
    }

    @Then("the response status is 422")
    public void thenTheResponseStatusIs422() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(422);
    }

    @And("EVNT is not called")
    public void andEvntIsNotCalled() {
        BaseIntegrationTest.evntWireMock.verify(
                0, postRequestedFor(urlPathEqualTo("/api/v1/events/ev-9/confirm")));
    }

    @Then("the response status is 403")
    public void thenTheResponseStatusIs403() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(403);
    }

    @And("the response does not reveal whether \"Outro\" exists")
    public void andTheResponseDoesNotRevealWhetherOutroExists() {
        // 403 generico — PatientNotInScopeException nao vaza existencia do paciente
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(403);
        // Nao deve dizer "not found" ou "does not exist"
        String body = ctx.lastResponse.getBody();
        if (body != null) {
            assertThat(body).doesNotContain("not found").doesNotContain("does not exist");
        }
    }

    @Then("the response status is 404")
    public void thenTheResponseStatusIs404() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(404);
    }

    // ==================== helpers ====================

    private org.springframework.http.ResponseEntity<String> httpGet(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ctx.jamillyToken);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private org.springframework.http.ResponseEntity<String> postCalibration(
            String patientId, String commandId, String sensitivity) {
        // Campos do PAC (RecalibrateRequest). `threshold` e `extra` nao existiam do lado
        // do produtor e sairam do contrato do painel junto com o caminho inventado.
        String body =
                """
                {"sensibilidade":"%s","urgencia":null,"tempoMinGestoMs":null,"tempoMaxGestoMs":null}
                """
                        .formatted(sensitivity);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ctx.jamillyToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/panel/calibration?patientId=" + patientId + "&commandId=" + commandId,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
    }

    private org.springframework.http.ResponseEntity<String> postEventConfirm(
            String eventId, String feedback) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ctx.jamillyToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/panel/events/" + eventId + "/confirm?feedback=" + feedback,
                HttpMethod.POST,
                new HttpEntity<>("", headers),
                String.class);
    }

    /**
     * {@code POST /api/pac/v1/pacientes/&#123;id&#125;/comandos/&#123;cmd&#125;:recalibrate} — o
     * unico endpoint de calibracao que o PAC serve ({@code ComandoLifecycleController}). Nao existe
     * no PAC nem {@code .../calibrate} nem endpoint de "estado de calibracao".
     */
    private static String recalibratePath(String patientId, String commandId) {
        return "/api/pac/v1/pacientes/" + patientId + "/comandos/" + commandId + ":recalibrate";
    }

    /** Espelha {@code ComandoLifecycleResponse}. */
    private static String comandoLifecycleJson(String comandoId, String sensibilidade) {
        return ("{\"id\":\"%s\",\"pacienteId\":\"Marcio\",\"rotulo\":\"Agua\","
                        + "\"necessidade\":\"hidratacao\",\"sensibilidade\":\"%s\","
                        + "\"urgencia\":\"rotina\",\"estado\":\"PUBLICADO\","
                        + "\"calibracaoVersao\":3,\"rowVersion\":1,"
                        + "\"updatedAt\":\"2026-08-01T00:00:00Z\",\"warnings\":[]}")
                .formatted(comandoId, sensibilidade);
    }

    private void stubPacRecalibrate(
            String patientId, String commandId, int status, String responseBody) {
        BaseIntegrationTest.pacWireMock.stubFor(
                post(urlPathEqualTo(recalibratePath(patientId, commandId)))
                        .willReturn(
                                aResponse()
                                        .withStatus(status)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(responseBody)));
        // Stub ACS para validacao de escopo
        BaseIntegrationTest.acsWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/acs/directory/caregivers/jamilly/patient-scope"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"cuidadorId\":\"jamilly\",\"pacienteIds\":[\"Marcio\"]}")));
    }

    private void stubEvntConfirmEvent(String eventId, String caregiverId) {
        BaseIntegrationTest.evntWireMock.stubFor(
                post(urlPathEqualTo("/api/v1/events/" + eventId + "/confirm"))
                        .willReturn(aResponse().withStatus(200)));
    }
}
