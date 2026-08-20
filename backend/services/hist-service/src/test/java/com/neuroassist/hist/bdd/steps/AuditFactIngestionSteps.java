package com.neuroassist.hist.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.neuroassist.hist.bdd.BddJwtTokenFactory;
import com.neuroassist.hist.bdd.BddScenarioState;
import com.neuroassist.hist.domain.model.HashChain;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — Audit fact ingestion is write-once and validated.
 * Feature: audit-fact-ingestion.feature
 */
public class AuditFactIngestionSteps {

    @Autowired
    private BddScenarioState state;

    @Autowired
    private BddJwtTokenFactory jwtFactory;

    @Autowired
    private SharedSteps shared;

    @Autowired
    private TestRestTemplate restTemplate;

    // ── Scenario 1: first record ──────────────────────────────────────────

    @Given("an audit fact \"018f-0001\" of type \"evento.confirmado\" for subject paciente \"pac-1\" by caregiver \"cg-1\"")
    public void givenAnAuditFact018f0001OfTypeEventoconfirmadoFor() {
        state.setPendingFactRequest(shared.buildFactRequest(
                state.factUUID("018f-0001"), "evento.confirmado", "paciente",
                state.subjectUUID("pac-1"), state.caregiverUUID("cg-1"), "Caregiver Alpha",
                Map.of("verdict", "confirmed"), Instant.now().minusSeconds(30), null, null));
    }

    @And("the record has sequenceNo 0")
    public void andTheRecordHasSequenceno0() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        assertThat(body.path("sequenceNo").asLong()).isEqualTo(0L);
    }

    @And("the record prevHash is the genesis constant")
    public void andTheRecordPrevhashIsTheGenesisConstant() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        assertThat(body.path("prevHash").asText()).isEqualTo(HashChain.GENESIS_PREV_HASH);
    }

    @And("the response has a Location header")
    public void andTheResponseHasALocationHeader() {
        assertThat(state.getLastResponse().getHeaders().getLocation())
                .as("Location header must be present after 201").isNotNull();
    }

    // ── Scenario 2: second fact continues the chain ───────────────────────

    @Given("the stream \"paciente:pac-1\" already has 1 record")
    public void givenTheStreamPacientepac1AlreadyHas1Record() {
        shared.ingestNFacts(state.subjectUUID("pac-1"), "paciente", 1);
    }

    @And("an audit fact \"018f-0002\" of type \"evento.descartado\" for subject paciente \"pac-1\" by caregiver \"cg-1\"")
    public void andAnAuditFact018f0002OfTypeEventodescartadoFor() {
        state.setPendingFactRequest(shared.buildFactRequest(
                state.factUUID("018f-0002"), "evento.descartado", "paciente",
                state.subjectUUID("pac-1"), state.caregiverUUID("cg-1"), "Caregiver Alpha",
                Map.of("verdict", "discarded"), Instant.now().minusSeconds(10), null, null));
    }

    @And("the record has sequenceNo 1")
    public void andTheRecordHasSequenceno1() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        assertThat(body.path("sequenceNo").asLong()).isEqualTo(1L);
    }

    @And("the record prevHash equals the previous record recordHash")
    public void andTheRecordPrevhashEqualsThePreviousRecordRecordhash() {
        assertThat(state.getLastIngestedRecordHash())
                .as("Previous record hash must have been captured during setup").isNotNull();
        JsonNode body = shared.parseBody(state.getLastResponse());
        assertThat(body.path("prevHash").asText()).isEqualTo(state.getLastIngestedRecordHash());
    }

    // ── Scenario 3: unknown factType ──────────────────────────────────────

    @Given("an audit fact \"018f-0003\" of type \"evento.xpto\" for subject paciente \"pac-1\" by caregiver \"cg-1\"")
    public void givenAnAuditFact018f0003OfTypeEventoxptoFor() {
        state.setPendingFactRequest(shared.buildFactRequest(
                state.factUUID("018f-0003"), "evento.xpto", "paciente",
                state.subjectUUID("pac-1"), state.caregiverUUID("cg-1"), "Caregiver Alpha",
                Map.of("key", "value"), Instant.now().minusSeconds(5), null, null));
    }

    @And("the problem type is \"/errors/unknown-fact-type\"")
    public void andTheProblemTypeIsErrorsunknownfacttype() {
        assertProblemType("/errors/unknown-fact-type");
    }

    // ── Scenario 4: forbidden payload ────────────────────────────────────

    @Given("an audit fact \"018f-0004\" of type \"evento.confirmado\" for subject paciente \"pac-1\" by caregiver \"cg-1\"")
    public void givenAnAuditFact018f0004OfTypeEventoconfirmadoFor() {
        state.setPendingFactRequest(shared.buildFactRequest(
                state.factUUID("018f-0004"), "evento.confirmado", "paciente",
                state.subjectUUID("pac-1"), state.caregiverUUID("cg-1"), "Caregiver Alpha",
                Map.of("verdict", "ok"), Instant.now().minusSeconds(5), null, null));
    }

    @And("its payload contains the key \"landmarks\"")
    public void andItsPayloadContainsTheKeyLandmarks() {
        Map<String, Object> existing = new HashMap<>(state.getPendingFactRequest());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                new HashMap<>((Map<String, Object>) existing.getOrDefault("payload", Map.of()));
        payload.put("landmarks", new double[]{0.1, 0.2, 0.3});
        existing.put("payload", payload);
        state.setPendingFactRequest(existing);
    }

    @And("the problem type is \"/errors/forbidden-payload\"")
    public void andTheProblemTypeIsErrorsforbiddenpayload() {
        assertProblemType("/errors/forbidden-payload");
    }

    // ── Scenario 5: correction ────────────────────────────────────────────

    @Given("a recorded fact \"018f-0002\" exists for subject paciente \"pac-1\"")
    public void givenARecordedFact018f0002ExistsForSubjectPaciente() {
        Map<String, Object> req = shared.buildFactRequest(
                state.factUUID("018f-0002"), "evento.descartado", "paciente",
                state.subjectUUID("pac-1"), state.caregiverUUID("cg-1"), "Caregiver Alpha",
                Map.of("verdict", "discarded"), Instant.now().minusSeconds(60), null, null);
        ResponseEntity<String> resp = shared.postAuditFact(req, state.getCurrentToken());
        assertThat(resp.getStatusCode().value()).isIn(200, 201);
        JsonNode body = shared.parseBody(resp);
        state.setNamedRecordId(UUID.fromString(body.path("id").asText()));
    }

    @And("an audit fact \"018f-0005\" of type \"registro.corrigido\" for subject paciente \"pac-1\" by caregiver \"cg-1\"")
    public void andAnAuditFact018f0005OfTypeRegistrocorrigidoFor() {
        state.setPendingFactRequest(shared.buildFactRequest(
                state.factUUID("018f-0005"), "registro.corrigido", "paciente",
                state.subjectUUID("pac-1"), state.caregiverUUID("cg-1"), "Caregiver Alpha",
                Map.of("reason", "data-correction"), Instant.now().minusSeconds(5), null, null));
    }

    @And("it corrects fact \"018f-0002\"")
    public void andItCorrectsFact018f0002() {
        Map<String, Object> req = new HashMap<>(state.getPendingFactRequest());
        req.put("correctsFactId", state.factUUID("018f-0002").toString());
        state.setPendingFactRequest(req);
    }

    @And("the record correctsRecordId points to the record of fact \"018f-0002\"")
    public void andTheRecordCorrectsrecordidPointsToTheRecordOf() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        String correctsRecordId = body.path("correctsRecordId").asText(null);
        assertThat(correctsRecordId).as("correctsRecordId must not be null").isNotNull().isNotEmpty();
        assertThat(UUID.fromString(correctsRecordId)).isEqualTo(state.getNamedRecordId());
    }

    @And("the original record of fact \"018f-0002\" is unchanged")
    public void andTheOriginalRecordOfFact018f0002IsUnchanged() {
        String token = jwtFactory.createToken(state.getTenantId(), "hist:read:all");
        ResponseEntity<String> resp = shared.getWithToken(
                "/api/v1/hist/audit-facts/" + state.getNamedRecordId(), token);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode body = shared.parseBody(resp);
        assertThat(UUID.fromString(body.path("factId").asText()))
                .isEqualTo(state.factUUID("018f-0002"));
    }

    // ── Scenario 6: no DELETE ──────────────────────────────────────────────

    @When("a DELETE is attempted on /audit-facts of that record")
    public void whenADeleteIsAttemptedOnAuditfactsOfThat() {
        UUID id = state.getNamedRecordId() != null ? state.getNamedRecordId() : UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(state.getCurrentToken());
        state.setLastResponse(restTemplate.exchange(
                "/api/v1/hist/audit-facts/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                String.class));
    }

    @Then("the response status is 405")
    public void thenTheResponseStatusIs405() {
        assertThat(state.getLastResponse().getStatusCode().value()).isEqualTo(405);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void assertProblemType(String expectedTypeSuffix) {
        JsonNode body = shared.parseBody(state.getLastResponse());
        String type = body.path("type").asText("");
        assertThat(type).as("ProblemDetail 'type' should end with " + expectedTypeSuffix)
                .endsWith(expectedTypeSuffix);
    }
}
