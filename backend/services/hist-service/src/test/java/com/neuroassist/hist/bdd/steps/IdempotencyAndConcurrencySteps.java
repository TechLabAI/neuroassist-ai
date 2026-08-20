package com.neuroassist.hist.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.neuroassist.hist.bdd.BddJwtTokenFactory;
import com.neuroassist.hist.bdd.BddScenarioState;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — Ingestion is exactly-once per factId and gap-free per stream. Feature:
 * idempotency-and-concurrency.feature
 */
public class IdempotencyAndConcurrencySteps {

    @Autowired private BddScenarioState state;

    @Autowired private BddJwtTokenFactory jwtFactory;

    @Autowired private SharedSteps shared;

    // ── Scenario 1: duplicate factId replay ──────────────────────────────

    @Given(
            "an audit fact \"018f-1001\" of type \"acesso.concedido\" for subject cuidador \"cg-9\" by caregiver \"cg-1\"")
    public void givenAnAuditFact018f1001OfTypeAcessoconcedidoFor() {
        state.setPendingFactRequest(
                shared.buildFactRequest(
                        state.factUUID("018f-1001"),
                        "acesso.concedido",
                        "cuidador",
                        state.subjectUUID("cg-9"),
                        state.caregiverUUID("cg-1"),
                        "Caregiver Bravo",
                        Map.of("granted", true),
                        Instant.now().minusSeconds(30),
                        null,
                        null));
    }

    @And("it was already submitted successfully")
    public void andItWasAlreadySubmittedSuccessfully() {
        ResponseEntity<String> first =
                shared.postAuditFact(state.getPendingFactRequest(), state.getCurrentToken());
        assertThat(first.getStatusCode().value()).isEqualTo(201);
        JsonNode body = shared.parseBody(first);
        state.setFirstRecordId(UUID.fromString(body.path("id").asText()));
    }

    @When("it is submitted to POST \\/audit-facts again")
    public void whenItIsSubmittedToPostAuditfactsAgain() {
        state.setLastResponse(
                shared.postAuditFact(state.getPendingFactRequest(), state.getCurrentToken()));
    }

    @And("the returned record id equals the original record id")
    public void andTheReturnedRecordIdEqualsTheOriginalRecord() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        UUID returnedId = UUID.fromString(body.path("id").asText());
        assertThat(returnedId).isEqualTo(state.getFirstRecordId());
    }

    @And("the stream \"cuidador:cg-9\" still has exactly 1 record")
    public void andTheStreamCuidadorcg9StillHasExactly1Record() {
        String token = jwtFactory.createToken(state.getTenantId(), "hist:read:all");
        String subjectId = state.subjectUUID("cg-9").toString();
        ResponseEntity<String> resp =
                shared.getWithToken(
                        "/api/v1/hist/history?subjectType=cuidador&subjectId=" + subjectId, token);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode body = shared.parseBody(resp);
        assertThat(body.path("total").asLong()).isEqualTo(1L);
    }

    // ── Scenario 2: consecutive sequence numbers (Outline) ───────────────

    @Given("the stream \"paciente:pac-2\" already has {int} records")
    public void givenTheStreamPacientepac2AlreadyHasExistingRecords(int existing) {
        if (existing > 0) {
            shared.ingestNFacts(state.subjectUUID("pac-2"), "paciente", existing);
        }
    }

    @And(
            "an audit fact {string} of type \"evento.confirmado\" for subject paciente \"pac-2\" by caregiver \"cg-1\"")
    public void andAnAuditFactFactidOfTypeEventoconfirmadoFor(String factId) {
        state.setPendingFactRequest(
                shared.buildFactRequest(
                        state.factUUID(factId),
                        "evento.confirmado",
                        "paciente",
                        state.subjectUUID("pac-2"),
                        state.caregiverUUID("cg-1"),
                        "Caregiver Bravo",
                        Map.of("step", factId),
                        Instant.now().minusSeconds(5),
                        null,
                        null));
    }

    // "the record has sequenceNo {int}" is handled by SharedSteps.thenTheRecordHasSequenceno(int)

    // ── Scenario 3: concurrent appends — no duplicate sequenceNo ─────────

    @Given("two audit facts \"018f-3001\" and \"018f-3002\" for subject paciente \"pac-3\"")
    public void givenTwoAuditFacts018f3001And018f3002ForSubject() {
        // Requests built on-the-fly in the When step
        state.getConcurrentResponses().clear();
    }

    @When("they are submitted concurrently to POST \\/audit-facts")
    public void whenTheyAreSubmittedConcurrentlyToPostAuditfacts() {
        Map<String, Object> req1 =
                shared.buildFactRequest(
                        state.factUUID("018f-3001"),
                        "evento.confirmado",
                        "paciente",
                        state.subjectUUID("pac-3"),
                        state.caregiverUUID("cg-1"),
                        "Caregiver Concurrent",
                        Map.of("seq", 1),
                        Instant.now().minusSeconds(5),
                        null,
                        null);
        Map<String, Object> req2 =
                shared.buildFactRequest(
                        state.factUUID("018f-3002"),
                        "evento.confirmado",
                        "paciente",
                        state.subjectUUID("pac-3"),
                        state.caregiverUUID("cg-1"),
                        "Caregiver Concurrent",
                        Map.of("seq", 2),
                        Instant.now().minusSeconds(4),
                        null,
                        null);
        String token = state.getCurrentToken();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<String>> task1 = () -> shared.postAuditFact(req1, token);
            Callable<ResponseEntity<String>> task2 = () -> shared.postAuditFact(req2, token);
            List<Future<ResponseEntity<String>>> futures =
                    executor.invokeAll(List.of(task1, task2));
            for (Future<ResponseEntity<String>> f : futures) {
                state.getConcurrentResponses().add(f.get());
            }
        } catch (Exception e) {
            throw new RuntimeException("Concurrent submission failed", e);
        } finally {
            executor.shutdown();
        }
    }

    @Then("both are recorded")
    public void thenBothAreRecorded() {
        assertThat(state.getConcurrentResponses()).hasSize(2);
        for (ResponseEntity<String> resp : state.getConcurrentResponses()) {
            assertThat(resp.getStatusCode().value())
                    .as("Both concurrent facts must be recorded")
                    .isIn(200, 201);
        }
    }

    @And("their sequenceNo values are 0 and 1 with no gap and no duplicate")
    public void andTheirSequencenoValuesAre0And1With() {
        List<Long> seqNos = new ArrayList<>();
        for (ResponseEntity<String> resp : state.getConcurrentResponses()) {
            seqNos.add(shared.parseBody(resp).path("sequenceNo").asLong());
        }
        assertThat(seqNos).hasSize(2);
        assertThat(seqNos).containsExactlyInAnyOrder(0L, 1L);
    }

    // ── Scenario 4: concurrent same factId — exactly-once ────────────────

    @Given(
            "an audit fact \"018f-4001\" of type \"dispositivo.pareado\" for subject dispositivo \"dev-1\" by caregiver \"cg-1\"")
    public void givenAnAuditFact018f4001OfTypeDispositivopareadoFor() {
        state.setPendingFactRequest(
                shared.buildFactRequest(
                        state.factUUID("018f-4001"),
                        "dispositivo.pareado",
                        "dispositivo",
                        state.subjectUUID("dev-1"),
                        state.caregiverUUID("cg-1"),
                        "Caregiver Delta",
                        Map.of("deviceId", "dev-1"),
                        Instant.now().minusSeconds(5),
                        null,
                        null));
    }

    @When("it is submitted concurrently by two nodes")
    public void whenItIsSubmittedConcurrentlyByTwoNodes() {
        Map<String, Object> req = state.getPendingFactRequest();
        String token = state.getCurrentToken();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        state.getConcurrentResponses().clear();
        try {
            Callable<ResponseEntity<String>> task = () -> shared.postAuditFact(req, token);
            List<Future<ResponseEntity<String>>> futures = executor.invokeAll(List.of(task, task));
            for (Future<ResponseEntity<String>> f : futures) {
                state.getConcurrentResponses().add(f.get());
            }
        } catch (Exception e) {
            throw new RuntimeException("Concurrent same-factId submission failed", e);
        } finally {
            executor.shutdown();
        }
    }

    @Then("exactly one record is persisted")
    public void thenExactlyOneRecordIsPersisted() {
        String token = jwtFactory.createToken(state.getTenantId(), "hist:read:all");
        String subjectId = state.subjectUUID("dev-1").toString();
        ResponseEntity<String> resp =
                shared.getWithToken(
                        "/api/v1/hist/history?subjectType=dispositivo&subjectId=" + subjectId,
                        token);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode body = shared.parseBody(resp);
        assertThat(body.path("total").asLong()).isEqualTo(1L);
    }

    @And("one response is 201 and the other is 200")
    public void andOneResponseIs201AndTheOtherIs() {
        assertThat(state.getConcurrentResponses()).hasSize(2);
        List<Integer> statuses =
                state.getConcurrentResponses().stream()
                        .map(r -> r.getStatusCode().value())
                        .sorted()
                        .toList();
        assertThat(statuses).containsExactly(200, 201);
    }
}
