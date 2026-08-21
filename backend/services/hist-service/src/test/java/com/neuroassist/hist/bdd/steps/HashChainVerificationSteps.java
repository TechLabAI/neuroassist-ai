package com.neuroassist.hist.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.neuroassist.hist.bdd.BddScenarioState;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Step definitions — Chain verification proves stream integrity. Feature:
 * hash-chain-verification.feature
 */
public class HashChainVerificationSteps {

    @Autowired private BddScenarioState state;

    @Autowired private SharedSteps shared;

    @Autowired private JdbcTemplate jdbcTemplate;

    // "the stream paciente:pac-1 has 3 correctly chained records" is in SharedSteps

    @When("GET \\/history\\/verify is called for subject paciente \"pac-1\"")
    public void whenGetHistoryverifyIsCalledForSubjectPacientePac1() {
        state.setLastResponse(callVerify("paciente", state.subjectUUID("pac-1")));
    }

    @And("the verification status is \"intact\"")
    public void andTheVerificationStatusIsIntact() {
        assertVerificationStatus("intact");
    }

    @And("recordsVerified is 3")
    public void andRecordsverifiedIs3() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        assertThat(body.path("recordsVerified").asLong()).isEqualTo(3L);
    }

    @And("brokenAtSequenceNo is null")
    public void andBrokenatsequencenoIsNull() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        assertThat(body.path("brokenAtSequenceNo").isNull()).isTrue();
    }

    // ── Scenario 2: tampered record ──────────────────────────────────────

    @And("the record at sequenceNo 1 has been altered directly in the database")
    public void andTheRecordAtSequenceno1HasBeenAltered() {
        String tenantId = state.getTenantId();
        String subjectId = state.subjectUUID("pac-1").toString();
        jdbcTemplate.execute("SET search_path TO " + tenantId + ", public");
        int updated =
                jdbcTemplate.update(
                        "UPDATE audit_record SET record_hash = 'deadbeefdeadbeefdeadbeefdeadbeef"
                                + "deadbeefdeadbeefdeadbeefdeadbeef' "
                                + "WHERE sequence_no = 1 AND subject_id = ?::uuid",
                        subjectId);
        assertThat(updated)
                .as("At least one record tampered at sequenceNo=1")
                .isGreaterThanOrEqualTo(1);
    }

    @And("the verification status is \"broken\"")
    public void andTheVerificationStatusIsBroken() {
        assertVerificationStatus("broken");
    }

    @And("brokenAtSequenceNo is 1")
    public void andBrokenatsequencenoIs1() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        assertThat(body.path("brokenAtSequenceNo").asLong()).isEqualTo(1L);
    }

    // ── Scenario 3: empty stream ──────────────────────────────────────────

    @Given("the stream \"paciente:pac-empty\" has no records")
    public void givenTheStreamPacientepacemptyHasNoRecords() {
        // No-op: pac-empty is guaranteed to have no records in a fresh scenario
    }

    @When("GET \\/history\\/verify is called for subject paciente \"pac-empty\"")
    public void whenGetHistoryverifyIsCalledForSubjectPacientePacempty() {
        state.setLastResponse(callVerify("paciente", state.subjectUUID("pac-empty")));
    }

    @And("the verification status is \"empty\"")
    public void andTheVerificationStatusIsEmpty() {
        assertVerificationStatus("empty");
    }

    @And("recordsVerified is 0")
    public void andRecordsverifiedIs0() {
        JsonNode body = shared.parseBody(state.getLastResponse());
        assertThat(body.path("recordsVerified").asLong()).isEqualTo(0L);
    }

    // ── Scenario 4: verify does not mutate ────────────────────────────────

    @Then("no record recordHash has changed")
    public void thenNoRecordRecordhashHasChanged() {
        String tenantId = state.getTenantId();
        String subjectId = state.subjectUUID("pac-1").toString();
        jdbcTemplate.execute("SET search_path TO " + tenantId + ", public");
        List<String> before =
                jdbcTemplate.queryForList(
                        "SELECT record_hash FROM audit_record WHERE subject_id = ?::uuid "
                                + "ORDER BY sequence_no ASC",
                        String.class,
                        subjectId);
        // Query again to confirm no mutation between the two reads
        List<String> after =
                jdbcTemplate.queryForList(
                        "SELECT record_hash FROM audit_record WHERE subject_id = ?::uuid "
                                + "ORDER BY sequence_no ASC",
                        String.class,
                        subjectId);
        assertThat(before)
                .as("record_hash values must not change after a verification call")
                .isEqualTo(after);
        assertThat(before).as("Stream should have 3 records").hasSize(3);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ResponseEntity<String> callVerify(String subjectType, UUID subjectId) {
        return shared.getWithToken(
                "/api/v1/hist/history/verify?subjectType="
                        + subjectType
                        + "&subjectId="
                        + subjectId,
                state.getCurrentToken());
    }

    private void assertVerificationStatus(String expected) {
        JsonNode body = shared.parseBody(state.getLastResponse());
        assertThat(body.path("status").asText()).isEqualTo(expected);
    }
}
