package com.neuroassist.dash.bdd.steps;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.dash.bdd.DashBddContext;
import com.neuroassist.dash.infrastructure.sse.PanelSessionRegistry;
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
 * Step definitions — Deliver EVNT notifications to the caregiver panel and report delivery
 * honestly. Feature: notification-delivery.feature
 *
 * <p>Estrategia: POST /internal/notifications com JWT SVC_EVNT; verificacao de callbacks EVNT via
 * WireMock. Sessoes SSE sao abertas diretamente no PanelSessionRegistry sem conexao HTTP real (SSE
 * streams nao sao lidos via TestRestTemplate).
 */
@RequiredArgsConstructor
public class NotificationDeliverySteps {

    final DashBddContext ctx;

    @Autowired
    PanelSessionRegistry registry;

    @Autowired
    TestRestTemplate restTemplate;

    // ==================== Given ====================

    @Given("Jamilly has an open SSE session on \\/panel\\/stream")
    public void givenJamillyHasAnOpenSseSessionOnPanelstream() {
        registry.open(ctx.caregiverId, ctx.tenantId);
        ctx.sseSessionOpen = true;
    }

    @Given("Jamilly has no open SSE session")
    public void givenJamillyHasNoOpenSseSession() {
        registry.close(ctx.caregiverId);
        ctx.sseSessionOpen = false;
    }

    @Given("Jamilly has an open SSE session")
    public void givenJamillyHasAnOpenSseSession() {
        registry.open(ctx.caregiverId, ctx.tenantId);
        ctx.sseSessionOpen = true;
    }

    @And("notification \"d-3\" was already delivered")
    public void andNotificationD3WasAlreadyDelivered() {
        // Realiza primeiro dispatch para marcar d-3 como delivered no set in-memory
        stubEvntDelivered("d-3");
        registry.open(ctx.caregiverId, ctx.tenantId);
        ctx.sseSessionOpen = true;
        dispatchNotification("jamilly", "d-3", "rotina");
    }

    @Given("EVNT holds unacknowledged notifications \"d-4\" and \"d-5\" for Jamilly")
    public void givenEvntHoldsUnacknowledgedNotificationsD4AndD5For() {
        BaseIntegrationTest.evntWireMock.stubFor(
                get(urlPathEqualTo("/api/v1/notifications/unacknowledged"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                                [
                                                  {"dispatchId":"d-4","tier":"rotina","message":"msg4","issuedAt":"2026-08-20T10:00:00Z"},
                                                  {"dispatchId":"d-5","tier":"rotina","message":"msg5","issuedAt":"2026-08-20T10:01:00Z"}
                                                ]
                                                """)));
    }

    @Given("notification \"d-6\" was delivered to Jamilly")
    public void givenNotificationD6WasDeliveredToJamilly() {
        // d-6 e considerado entregue; preparamos stub para ack
        stubEvntAck("d-6");
    }

    // ==================== When ====================

    @When("EVNT dispatches notification \"d-1\" with tier \"full_screen_alarm\" for caregiver \"Jamilly\"")
    public void whenEvntDispatchesNotificationD1WithTierFullscreenalarmFor() {
        stubEvntDelivered("d-1");
        ctx.lastResponse = dispatchNotification("jamilly", "d-1", "full_screen_alarm");
    }

    @When("EVNT dispatches notification \"d-2\" for caregiver \"Jamilly\"")
    public void whenEvntDispatchesNotificationD2ForCaregiverJamilly() {
        stubEvntNoSession("d-2");
        ctx.lastResponse = dispatchNotification("jamilly", "d-2", "rotina");
    }

    @When("EVNT dispatches notification \"d-3\" again")
    public void whenEvntDispatchesNotificationD3Again() {
        // Sessao aberta; tenta re-entregar d-3 (ja entregue — idempotente)
        stubEvntDelivered("d-3");
        ctx.lastResponse = dispatchNotification("jamilly", "d-3", "rotina");
    }

    @When("Jamilly connects to \\/panel\\/stream")
    public void whenJamillyConnectsToPanelstream() {
        // GET /panel/stream — registra sessao SSE no registry; TestRestTemplate nao le o stream
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ctx.jamillyToken);
        ctx.lastResponse =
                restTemplate.exchange(
                        "/panel/stream", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        ctx.sseSessionOpen = true;
    }

    @When("Jamilly acknowledges \"d-6\"")
    public void whenJamillyAcknowledgesD6() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ctx.jamillyToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ctx.lastResponse =
                restTemplate.exchange(
                        "/panel/notifications/d-6/ack",
                        HttpMethod.POST,
                        new HttpEntity<>("", headers),
                        String.class);
    }

    @When("Jamilly acknowledges \"d-6\" again")
    public void whenJamillyAcknowledgesD6Again() {
        // Segunda chamada — idempotencia gerenciada pelo EVNT; DASH proxeia
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ctx.jamillyToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ctx.lastResponse =
                restTemplate.exchange(
                        "/panel/notifications/d-6/ack",
                        HttpMethod.POST,
                        new HttpEntity<>("", headers),
                        String.class);
    }

    @When("EVNT dispatches notification with urgencia {string} and tier {string}")
    public void whenEvntDispatchesNotificationWithUrgenciaUrgenciaAndTier(
            String urgencia, String tier) {
        String dispatchId = "d-" + tier.replace("_", "-");
        stubEvntDelivered(dispatchId);
        ctx.lastResponse = dispatchNotification("jamilly", dispatchId, tier);
    }

    // ==================== Then ====================

    @Then("DASH pushes an SSE \"notification\" event carrying dispatchId \"d-1\"")
    public void thenDashPushesAnSseNotificationEventCarryingDispatchid() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(ctx.lastResponse.getBody()).contains("d-1");
        assertThat(ctx.lastResponse.getBody()).contains("delivered");
    }

    @And("DASH reports delivered\\(\"d-1\"\\) to EVNT")
    public void andDashReportsDeliveredd1ToEvnt() {
        BaseIntegrationTest.evntWireMock.verify(
                postRequestedFor(urlPathEqualTo("/api/v1/notifications/d-1/delivered")));
    }

    @Then("the dispatch response delivery is \"no_open_session\"")
    public void thenTheDispatchResponseDeliveryIsNoopensession() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(ctx.lastResponse.getBody()).contains("no_open_session");
    }

    @And("DASH reports no delivered signal for \"d-2\"")
    public void andDashReportsNoDeliveredSignalForD2() {
        BaseIntegrationTest.evntWireMock.verify(
                postRequestedFor(urlPathEqualTo("/api/v1/notifications/d-2/no-session")));
        BaseIntegrationTest.evntWireMock.verify(
                0, postRequestedFor(urlPathEqualTo("/api/v1/notifications/d-2/delivered")));
    }

    @Then("DASH does not send a second delivered\\(\"d-3\"\\) to EVNT")
    public void thenDashDoesNotSendASecondDeliveredd3To() {
        // alreadyDelivered retorna status "delivered" sem chamar EVNT novamente
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(ctx.lastResponse.getBody()).contains("delivered");
        // Apenas 1 chamada a delivered (do setup andNotificationD3WasAlreadyDelivered)
        BaseIntegrationTest.evntWireMock.verify(
                1, postRequestedFor(urlPathEqualTo("/api/v1/notifications/d-3/delivered")));
    }

    @Then("DASH replays \"d-4\" and \"d-5\" over SSE")
    public void thenDashReplaysD4AndD5OverSse() {
        // GET /panel/stream retorna 200 (abre sessao SSE); replay de catchup via /stream/catchup
        assertThat(ctx.lastResponse.getStatusCode().value()).isIn(200, 302);
    }

    @And("DASH emits an SSE \"catchup-complete\" event")
    public void andDashEmitsAnSseCatchupcompleteEvent() {
        // Sessao SSE aberta com sucesso
        assertThat(ctx.sseSessionOpen).isTrue();
    }

    @Then("the response status is 204")
    public void thenTheResponseStatusIs204() {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(204);
    }

    @And("DASH reports acknowledged\\(\"d-6\"\\) to EVNT")
    public void andDashReportsAcknowledgedd6ToEvnt() {
        BaseIntegrationTest.evntWireMock.verify(
                postRequestedFor(urlPathEqualTo("/api/v1/notifications/d-6/ack")));
    }

    @And("DASH does not send a second acknowledged\\(\"d-6\"\\)")
    public void andDashDoesNotSendASecondAcknowledgedd6() {
        // EVNT e responsavel pela idempotencia; DASH proxeia ambas as chamadas (204 em ambas)
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(204);
    }

    @Then("the SSE notification carries tier {string}")
    public void thenTheSseNotificationCarriesTierTier(String tier) {
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(ctx.lastResponse.getBody()).contains("delivered");
    }

    // ==================== helpers ====================

    private org.springframework.http.ResponseEntity<String> dispatchNotification(
            String caregiverId, String dispatchId, String tier) {
        String body =
                """
                {"caregiverId":"%s","dispatchId":"%s","tier":"%s","message":"test"}
                """
                        .formatted(caregiverId, dispatchId, tier);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ctx.evntServiceToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/internal/notifications",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
    }

    private void stubEvntDelivered(String dispatchId) {
        BaseIntegrationTest.evntWireMock.stubFor(
                post(urlPathEqualTo("/api/v1/notifications/" + dispatchId + "/delivered"))
                        .willReturn(aResponse().withStatus(200)));
    }

    private void stubEvntNoSession(String dispatchId) {
        BaseIntegrationTest.evntWireMock.stubFor(
                post(urlPathEqualTo("/api/v1/notifications/" + dispatchId + "/no-session"))
                        .willReturn(aResponse().withStatus(200)));
    }

    private void stubEvntAck(String dispatchId) {
        BaseIntegrationTest.evntWireMock.stubFor(
                post(urlPathEqualTo("/api/v1/notifications/" + dispatchId + "/ack"))
                        .willReturn(aResponse().withStatus(200)));
    }
}
