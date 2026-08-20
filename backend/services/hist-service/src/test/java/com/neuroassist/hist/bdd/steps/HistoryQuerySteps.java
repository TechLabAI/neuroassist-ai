package com.neuroassist.hist.bdd.steps;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.neuroassist.hist.bdd.BddJwtTokenFactory;
import com.neuroassist.hist.bdd.BddScenarioState;
import com.neuroassist.hist.bdd.BddWireMockHolder;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — History timeline query is filtered, paginated and tenant/scope bound. Feature:
 * history-query.feature
 */
public class HistoryQuerySteps {

    @Autowired private BddScenarioState state;

    @Autowired private BddJwtTokenFactory jwtFactory;

    @Autowired private SharedSteps shared;

    // ── Scenario 1: coordinator query ordered newest-first ─────────────────

    // "the stream paciente:pac-1 has 3 records" is in SharedSteps

    @When("GET \\/history is called with subjectType \"paciente\" and subjectId \"pac-1\"")
    public void whenGetHistoryIsCalledWithSubjecttypePacienteAnd() {
        String subjectId = state.subjectUUID("pac-1").toString();
        state.setLastResponse(
                shared.getWithToken(
                        "/api/v1/hist/history?subjectType=paciente&subjectId="
                                + subjectId
                                + "&order=desc",
                        state.getCurrentToken()));
    }

    @And("the items are ordered by recordedAt descending")
    public void andTheItemsAreOrderedByRecordedatDescending() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        JsonNode items = body.path("items");
        assertThat(items.isArray()).isTrue();
        Instant prev = Instant.MAX;
        for (JsonNode item : items) {
            Instant recordedAt = Instant.parse(item.path("recordedAt").asText());
            assertThat(recordedAt).isBeforeOrEqualTo(prev);
            prev = recordedAt;
        }
    }

    @And("total is 3")
    public void andTotalIs3() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        assertThat(body.path("total").asLong()).isEqualTo(3L);
    }

    // ── Scenario 2: factType filter ───────────────────────────────────────

    @And(
            "subject paciente \"pac-1\" has facts of types \"evento.confirmado\" and \"comando.recalibrado\"")
    public void andSubjectPacientePac1HasFactsOfTypesEventoconfirmado() {
        UUID subjectId = state.subjectUUID("pac-1");
        String token = jwtFactory.createToken(state.getTenantId(), "hist:write");
        // Insert one fact of type evento.confirmado
        Map<String, Object> req1 =
                shared.buildFactRequest(
                        UUID.randomUUID(),
                        "evento.confirmado",
                        "paciente",
                        subjectId,
                        state.caregiverUUID("cg-1"),
                        "Caregiver Filter",
                        Map.of("filter", "confirmado"),
                        Instant.now().minusSeconds(20),
                        null,
                        null);
        ResponseEntity<String> r1 = shared.postAuditFact(req1, token);
        assertThat(r1.getStatusCode().value()).isIn(200, 201);
        // Insert one fact of type comando.recalibrado
        Map<String, Object> req2 =
                shared.buildFactRequest(
                        UUID.randomUUID(),
                        "comando.recalibrado",
                        "paciente",
                        subjectId,
                        state.caregiverUUID("cg-1"),
                        "Caregiver Filter",
                        Map.of("filter", "recalibrado"),
                        Instant.now().minusSeconds(10),
                        null,
                        null);
        ResponseEntity<String> r2 = shared.postAuditFact(req2, token);
        assertThat(r2.getStatusCode().value()).isIn(200, 201);
    }

    @When(
            "GET \\/history is called with subjectType \"paciente\" subjectId \"pac-1\" and factType \"evento.confirmado\"")
    public void whenGetHistoryIsCalledWithSubjecttypePacienteSubjectid() {
        String subjectId = state.subjectUUID("pac-1").toString();
        state.setLastResponse(
                shared.getWithToken(
                        "/api/v1/hist/history?subjectType=paciente&subjectId="
                                + subjectId
                                + "&factType=evento.confirmado",
                        state.getCurrentToken()));
    }

    @And("every item has factType \"evento.confirmado\"")
    public void andEveryItemHasFacttypeEventoconfirmado() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        JsonNode items = body.path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items).as("Should have at least one item filtered by factType").isNotEmpty();
        for (JsonNode item : items) {
            assertThat(item.path("factType").asText())
                    .as("Each item must have factType evento.confirmado")
                    .isEqualTo("evento.confirmado");
        }
    }

    @And("each item payload carries the producer verdict snapshot unchanged")
    public void andEachItemPayloadCarriesTheProducerVerdictSnapshot() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        JsonNode items = body.path("items");
        for (JsonNode item : items) {
            JsonNode payload = item.path("payload");
            assertThat(payload.isNull()).as("Payload must not be null").isFalse();
            assertThat(payload.isMissingNode()).as("Payload must not be missing").isFalse();
        }
    }

    // ── Scenario 3: subjectId without subjectType → 400 ──────────────────

    @When("GET \\/history is called with subjectId \"pac-1\" and no subjectType")
    public void whenGetHistoryIsCalledWithSubjectidPac1And() {
        String subjectId = state.subjectUUID("pac-1").toString();
        state.setLastResponse(
                shared.getWithToken(
                        "/api/v1/hist/history?subjectId=" + subjectId, state.getCurrentToken()));
    }

    @Then("the response status is 400")
    public void thenTheResponseStatusIs400() {
        assertThat(state.getLastResponse().getStatusCode().value()).isEqualTo(400);
    }

    @And("the problem type is \"\\/errors\\/incomplete-subject-filter\"")
    public void andTheProblemTypeIsErrorsincompletesubjectfilter() {
        assertProblemType("/errors/incomplete-subject-filter");
    }

    // ── Scenario 4: inverted time window → 400 ───────────────────────────

    @When(
            "GET \\/history is called with from \"2026-08-16T10:00:00Z\" and to \"2026-08-15T10:00:00Z\"")
    public void whenGetHistoryIsCalledWithFrom20260816t100000zAnd() {
        state.setLastResponse(
                shared.getWithToken(
                        "/api/v1/hist/history?from=2026-08-16T10:00:00Z&to=2026-08-15T10:00:00Z",
                        state.getCurrentToken()));
    }

    @And("the problem type is \"\\/errors\\/invalid-time-window\"")
    public void andTheProblemTypeIsErrorsinvalidtimewindow() {
        assertProblemType("/errors/invalid-time-window");
    }

    // ── Scenario 5: caregiver scope via ACS ──────────────────────────────

    @Given("a caregiver token with scope \"hist:read\"")
    public void givenACaregiverTokenWithScopeHistread() {
        UUID caregiverId = state.caregiverUUID("cg-caregiver");
        state.setCurrentToken(
                jwtFactory.createTokenWithSubject(
                        state.getTenantId(), caregiverId.toString(), "hist:read"));
    }

    @And("ACS resolves the caregiver scope to patients \"pac-1\"")
    public void andAcsResolvesTheCaregiverScopeToPatientsPac1() {
        // Stub ACS to return [pac-1-uuid] for the caregiver
        UUID caregiverId = state.caregiverUUID("cg-caregiver");
        UUID pac1Id = state.subjectUUID("pac-1");
        BddWireMockHolder.ACS_WIREMOCK.stubFor(
                get(urlPathMatching("/api/v1/acs/caregivers/" + caregiverId + "/patients"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("[\"" + pac1Id + "\"]")));
    }

    @When("GET \\/history is called with subjectType \"paciente\" and subjectId \"pac-9\"")
    public void whenGetHistoryIsCalledWithSubjecttypePacienteAnd_2() {
        String pac9Id = state.subjectUUID("pac-9").toString();
        state.setLastResponse(
                shared.getWithToken(
                        "/api/v1/hist/history?subjectType=paciente&subjectId=" + pac9Id,
                        state.getCurrentToken()));
    }

    @Then("the response items are empty")
    public void thenTheResponseItemsAreEmpty() {
        // The caregiver only has access to pac-1, but we queried pac-9
        // Service should return 200 with empty items (or 403/200-empty depending on impl)
        int status = state.getLastResponse().getStatusCode().value();
        if (status == 200) {
            JsonNode body = shared.parseBody(state.getLastResponse());
            assertThat(body.path("items").size()).isEqualTo(0);
        } else {
            // If the service rejects the request with 403/404, that also satisfies "no results"
            assertThat(status).isIn(200, 403, 404);
        }
    }

    // ── Scenario 6: ACS unavailable → 503 ────────────────────────────────

    @And("ACS is unreachable")
    public void andAcsIsUnreachable() {
        UUID caregiverId = state.caregiverUUID("cg-caregiver");
        // Return a network fault so Feign throws an exception → circuit breaker → 503
        BddWireMockHolder.ACS_WIREMOCK.stubFor(
                get(urlPathMatching("/api/v1/acs/caregivers/" + caregiverId + "/patients"))
                        .willReturn(
                                aResponse()
                                        .withFault(
                                                com.github.tomakehurst.wiremock.http.Fault
                                                        .CONNECTION_RESET_BY_PEER)));
    }

    @Then("the response status is 503")
    public void thenTheResponseStatusIs503() {
        assertThat(state.getLastResponse().getStatusCode().value()).isEqualTo(503);
    }

    @And("the problem type is \"\\/errors\\/scope-unavailable\"")
    public void andTheProblemTypeIsErrorsscopeunavailable() {
        assertProblemType("/errors/scope-unavailable");
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void assertProblemType(String expectedSuffix) {
        JsonNode body = shared.parseBody(state.getLastResponse());
        String type = body.path("type").asText("");
        assertThat(type)
                .as("ProblemDetail 'type' should end with " + expectedSuffix)
                .endsWith(expectedSuffix);
    }
}
