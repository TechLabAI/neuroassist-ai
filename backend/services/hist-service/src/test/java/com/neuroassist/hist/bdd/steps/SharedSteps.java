package com.neuroassist.hist.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neuroassist.hist.bdd.BddJwtTokenFactory;
import com.neuroassist.hist.bdd.BddScenarioState;
import io.cucumber.java.Before;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared step definitions — steps common to two or more feature files.
 *
 * <p>Background steps for tenant provisioning, token creation, and common response assertions.
 */
public class SharedSteps {

    @Autowired private BddScenarioState state;

    @Autowired private BddJwtTokenFactory jwtFactory;

    @Autowired private TestRestTemplate restTemplate;

    @Autowired private JdbcTemplate jdbcTemplate;

    final ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Truncates all tenant-specific ledger tables before each scenario so that sequenceNo counters
     * and stream heads are reset to a clean state. Uses schema-qualified table names to bypass the
     * multi-tenant search_path mechanism.
     *
     * <p>Order 0 — runs before all other @Before hooks (including resetWireMock).
     */
    @Before(order = 0)
    public void cleanDatabase() {
        String[] tenantSchemas = {"t_emp_test", "t_emp_other"};
        String[] tables = {"audit_record", "audit_stream_head", "idempotency_entry"};
        for (String schema : tenantSchemas) {
            for (String table : tables) {
                try {
                    jdbcTemplate.execute("TRUNCATE " + schema + "." + table + " CASCADE");
                } catch (Exception ignored) {
                    // Schema or table may not exist for every test run — safe to skip
                }
            }
        }
    }

    @Before(order = 1)
    public void resetWireMock() {
        com.neuroassist.hist.bdd.BddWireMockHolder.ACS_WIREMOCK.resetAll();
    }

    // ------------------------------------------------------------------ tenant

    @Given("a provisioned tenant \"familia-silva\"")
    public void givenAProvisionedTenantFamiliasilva() {
        state.setTenantId("t_emp_test");
    }

    // ------------------------------------------------------------------ tokens

    @And("a producer token with scope \"hist:write\"")
    public void andAProducerTokenWithScopeHistwrite() {
        state.setCurrentToken(jwtFactory.createToken(state.getTenantId(), "hist:write"));
    }

    @And("a coordinator token with scope \"hist:read:all\"")
    public void andACoordinatorTokenWithScopeHistreadall() {
        state.setCurrentToken(jwtFactory.createToken(state.getTenantId(), "hist:read:all"));
    }

    // ------------------------------------------------------------------ stream setup helpers

    @Given("the stream \"paciente:pac-1\" has 3 records")
    public void givenTheStreamPacientepac1Has3Records() {
        ingestNFacts(state.subjectUUID("pac-1"), "paciente", 3);
    }

    @Given("the stream \"paciente:pac-1\" has 3 correctly chained records")
    public void givenTheStreamPacientepac1Has3CorrectlyChainedRecords() {
        ingestNFacts(state.subjectUUID("pac-1"), "paciente", 3);
    }

    // ------------------------------------------------------------------ submission

    @When("it is submitted to POST \\/audit-facts")
    public void whenItIsSubmittedToPostAuditfacts() {
        state.setLastResponse(
                postAuditFact(state.getPendingFactRequest(), state.getCurrentToken()));
    }

    // ------------------------------------------------------------------ common assertions

    @Then("the response status is 201")
    public void thenTheResponseStatusIs201() {
        assertThat(state.getLastResponse().getStatusCode().value()).isEqualTo(201);
    }

    @Then("the response status is 200")
    public void thenTheResponseStatusIs200() {
        assertThat(state.getLastResponse().getStatusCode().value()).isEqualTo(200);
    }

    @Then("the response status is 422")
    public void thenTheResponseStatusIs422() {
        assertThat(state.getLastResponse().getStatusCode().value()).isEqualTo(422);
    }

    /**
     * Unified sequenceNo assertion used by both audit-fact-ingestion.feature and the
     * idempotency-and-concurrency.feature Scenario Outline. Uses {int} so literal "0", "1", "5" in
     * feature text are matched without ambiguity.
     */
    @Then("the record has sequenceNo {int}")
    public void thenTheRecordHasSequenceno(int expected) {
        JsonNode body = parseBody(state.getLastResponse());
        assertThat(body.path("sequenceNo").asLong()).isEqualTo((long) expected);
    }

    // ------------------------------------------------------------------ HTTP helpers
    // (package-visible)

    ResponseEntity<String> postAuditFact(Map<String, Object> requestBody, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        try {
            String body = mapper.writeValueAsString(requestBody);
            return restTemplate.exchange(
                    "/api/v1/hist/audit-facts",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to POST audit-fact", e);
        }
    }

    ResponseEntity<String> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    void ingestNFacts(UUID subjectId, String subjectType, int n) {
        String producerToken = jwtFactory.createToken(state.getTenantId(), "hist:write");
        for (int i = 0; i < n; i++) {
            UUID factId = UUID.randomUUID();
            Map<String, Object> req =
                    buildFactRequest(
                            factId,
                            "evento.confirmado",
                            subjectType,
                            subjectId,
                            state.caregiverUUID("cg-1"),
                            "Caregiver Test",
                            Map.of("step", i),
                            Instant.now().minusSeconds(n - i),
                            null,
                            null);
            ResponseEntity<String> resp = postAuditFact(req, producerToken);
            assertThat(resp.getStatusCode().value())
                    .as("Setup ingestion of fact %d should succeed", i)
                    .isIn(201, 200);
            try {
                JsonNode body = parseBody(resp);
                String hash = body.path("recordHash").asText(null);
                if (hash != null) {
                    state.setLastIngestedRecordHash(hash);
                }
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    Map<String, Object> buildFactRequest(
            UUID factId,
            String factType,
            String subjectType,
            UUID subjectId,
            UUID caregiverId,
            String caregiverName,
            Map<String, Object> payload,
            Instant occurredAt,
            UUID correctsFactId,
            String empresaId) {
        Map<String, Object> subject = Map.of("type", subjectType, "id", subjectId.toString());
        Map<String, Object> actor =
                Map.of("caregiverId", caregiverId.toString(), "displayNameSnapshot", caregiverName);
        Map<String, Object> req = new HashMap<>();
        req.put("factId", factId.toString());
        req.put("factType", factType);
        req.put("subject", subject);
        req.put("actor", actor);
        req.put("payload", payload != null ? payload : Map.of("key", "value"));
        req.put(
                "occurredAt",
                occurredAt != null ? occurredAt.toString() : Instant.now().toString());
        if (correctsFactId != null) {
            req.put("correctsFactId", correctsFactId.toString());
        }
        if (empresaId != null) {
            req.put("empresaId", empresaId);
        }
        return req;
    }

    JsonNode parseBody(ResponseEntity<String> response) {
        try {
            return mapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response body: " + response.getBody(), e);
        }
    }
}
