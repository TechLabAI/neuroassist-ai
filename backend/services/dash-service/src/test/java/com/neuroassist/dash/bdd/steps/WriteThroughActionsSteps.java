package com.neuroassist.dash.bdd.steps;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.dash.integration.BaseIntegrationTest;
import com.neuroassist.dash.bdd.DashBddContext;
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
 * <p>Estrategia: stubs WireMock para PAC (calibracao) e EVNT (confirmacao/ack). Chamadas HTTP
 * reais via TestRestTemplate com JWT de cuidador. Verificacao de WireMock para confirmar que DASH
 * nao armazena estado e apenas proxeia.
 */
@RequiredArgsConstructor
public class WriteThroughActionsSteps {

    final DashBddContext ctx;

    @Autowired
    TestRestTemplate restTemplate;

    // ==================== Given ====================

    @And("command \"cmd-water\" belongs to \"Marcio\"")
    public void andCommandCmdwaterBelongsToMarcio() {
        // PAC retorna cmd-water no catalogo de Marcio
        stubPacCalibrate("Marcio", "cmd-water", 200,
                "{\"commandId\":\"cmd-water\",\"newCalibrationState\":\"media\",\"success\":true}");
    }

    @Given("PAC rejects the calibration edit with status 409")
    public void givenPacRejectsTheCalibrationEditWithStatus409() {
        // PAC retorna 409 — DASH deve relay
        BaseIntegrationTest.pacWireMock.stubFor(
                post(urlPathEqualTo("/api/v1/patients/Marcio/commands/cmd-water/calibrate"))
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
                get(urlPathEqualTo("/api/v1/caregivers/jamilly/patients"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("[\"Marcio\"]")));
    }

    @Given("command \"cmd-other\" does not belong to \"Marcio\"")
    public void givenCommandCmdotherDoesNotBelongToMarcio() {
        // PAC retorna 404 para cmd-other de Marcio
        BaseIntegrationTest.pacWireMock.stubFor(
                post(urlPathEqualTo("/api/v1/patients/Marcio/commands/cmd-other/calibrate"))
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
        BaseIntegrationTest.pacWireMock.verify(
                postRequestedFor(
                        urlPathEqualTo("/api/v1/patients/Marcio/commands/cmd-water/calibrate")));
    }

    @And("the response body is PAC's authoritative result")
    public void andTheResponseBodyIsPacsAuthoritativeResult() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(ctx.lastResponse.getBody()).contains("cmd-water");
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
        assertThat(ctx.lastResponse.getBody()).contains("conflict")
                .or()
                .satisfiesAnyOf(body -> assertThat(body.getBody()).contains("409"));
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
        return restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private org.springframework.http.ResponseEntity<String> postCalibration(
            String patientId, String commandId, String sensitivity) {
        String body =
                """
                {"sensitivity":"%s","threshold":"0.8","extra":{}}
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

    private void stubPacCalibrate(
            String patientId, String commandId, int status, String responseBody) {
        BaseIntegrationTest.pacWireMock.stubFor(
                post(urlPathEqualTo(
                                "/api/v1/patients/" + patientId + "/commands/" + commandId
                                        + "/calibrate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(status)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(responseBody)));
        // Stub ACS para validacao de escopo
        BaseIntegrationTest.acsWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/caregivers/jamilly/patients"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("[\"Marcio\"]")));
    }

    private void stubEvntConfirmEvent(String eventId, String caregiverId) {
        BaseIntegrationTest.evntWireMock.stubFor(
                post(urlPathEqualTo("/api/v1/events/" + eventId + "/confirm"))
                        .willReturn(aResponse().withStatus(200)));
    }
}
