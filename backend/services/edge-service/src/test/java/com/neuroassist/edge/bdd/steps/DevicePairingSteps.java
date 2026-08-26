package com.neuroassist.edge.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.edge.bdd.TestContext;
import com.neuroassist.edge.bdd.TestJwtFactory;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Step definitions — Exclusive device pairing. Feature: device-pairing.feature
 *
 * <p>Implemented in P13 (EDGE-020). Shared steps live in SharedSteps.java.
 *
 * <p>All DB operations use fully qualified {@code t_emp_test.*} names because {@code JdbcTemplate}
 * bypasses Hibernate's multi-tenancy.
 */
public class DevicePairingSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Autowired private TestContext testContext;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private TestRestTemplate restTemplate;

    // -------------------------------------------------------------------------
    // State-setup Given steps
    // -------------------------------------------------------------------------

    /** Inserts device "d7b1" in the 'registered' state (initial device lifecycle state). */
    @Given("device \"d7b1\" is in state \"registered\"")
    public void givenDeviceD7b1IsInStateRegistered() {
        jdbcTemplate.update(
                "INSERT INTO t_emp_test.dispositivo_edge"
                        + " (id, tenant_id, pairing_state, label, row_version)"
                        + " VALUES (?, ?, 'registered', 'test-device-d7b1', 0)",
                TestJwtFactory.D7B1_ID,
                TestJwtFactory.TEST_TENANT);
    }

    /**
     * No-op: BddHooks.cleanUpAndResetStubs() truncates the dispositivo_edge table before each
     * scenario, so patient "9c2a" has no paired device at the start of every scenario.
     */
    @And("patient \"9c2a\" has no paired device")
    public void andPatient9c2aHasNoPairedDevice() {
        // Verified implicitly: after TRUNCATE, no paired device exists for any patient.
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_emp_test.dispositivo_edge"
                                + " WHERE paciente_id = ? AND pairing_state = 'paired'",
                        Integer.class,
                        TestJwtFactory.PATIENT_9C2A_ID);
        assertThat(count)
                .as("patient 9c2a must have no paired device at start of scenario")
                .isEqualTo(0);
    }

    /** Inserts device "d7b1" in the 'paired' state, associated with patient "9c2a". */
    @Given("patient \"9c2a\" already has device \"d7b1\" in state \"paired\"")
    public void givenPatient9c2aAlreadyHasDeviceD7b1InState() {
        insertPairedDevice(
                TestJwtFactory.D7B1_ID, TestJwtFactory.PATIENT_9C2A_ID, "test-device-d7b1");
    }

    /** Inserts device "e8c2" in the 'registered' state. */
    @And("device \"e8c2\" is in state \"registered\"")
    public void andDeviceE8c2IsInStateRegistered() {
        jdbcTemplate.update(
                "INSERT INTO t_emp_test.dispositivo_edge"
                        + " (id, tenant_id, pairing_state, label, row_version)"
                        + " VALUES (?, ?, 'registered', 'test-device-e8c2', 0)",
                TestJwtFactory.E8C2_ID,
                TestJwtFactory.TEST_TENANT);
    }

    /** Inserts device "d7b1" in the 'paired' state associated with patient "9c2a". */
    @Given("device \"d7b1\" is in state \"paired\" to patient \"9c2a\"")
    public void givenDeviceD7b1IsInStatePairedToPatient() {
        insertPairedDevice(
                TestJwtFactory.D7B1_ID, TestJwtFactory.PATIENT_9C2A_ID, "test-device-d7b1");
    }

    /** Inserts device "e8c2" in the 'unpaired' state. */
    @Given("device \"e8c2\" is in state \"unpaired\"")
    public void givenDeviceE8c2IsInStateUnpaired() {
        jdbcTemplate.update(
                "INSERT INTO t_emp_test.dispositivo_edge"
                        + " (id, tenant_id, pairing_state, label, row_version)"
                        + " VALUES (?, ?, 'unpaired', 'test-device-e8c2', 0)",
                TestJwtFactory.E8C2_ID,
                TestJwtFactory.TEST_TENANT);
    }

    /** No-op: after TRUNCATE, patient "1f4b" has no paired device. */
    @And("patient \"1f4b\" has no paired device")
    public void andPatient1f4bHasNoPairedDevice() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_emp_test.dispositivo_edge"
                                + " WHERE paciente_id = ? AND pairing_state = 'paired'",
                        Integer.class,
                        TestJwtFactory.PATIENT_1F4B_ID);
        assertThat(count)
                .as("patient 1f4b must have no paired device at start of scenario")
                .isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // When — pairing actions
    // -------------------------------------------------------------------------

    /** POST /api/v1/edge/devices/{D7B1_ID}/pairings with pacienteId = PATIENT_9C2A_ID. */
    @When("the caller pairs device \"d7b1\" to patient \"9c2a\"")
    public void whenTheCallerPairsDeviceD7b1ToPatient9c2a() {
        testContext.setLastResponse(
                performPair(TestJwtFactory.D7B1_ID, TestJwtFactory.PATIENT_9C2A_ID));
    }

    /** POST /api/v1/edge/devices/{E8C2_ID}/pairings with pacienteId = PATIENT_9C2A_ID. */
    @When("the caller pairs device \"e8c2\" to patient \"9c2a\"")
    public void whenTheCallerPairsDeviceE8c2ToPatient9c2a() {
        testContext.setLastResponse(
                performPair(TestJwtFactory.E8C2_ID, TestJwtFactory.PATIENT_9C2A_ID));
    }

    /** POST /api/v1/edge/devices/{D7B1_ID}/pairings with pacienteId = PATIENT_1F4B_ID. */
    @When("the caller pairs device \"d7b1\" to patient \"1f4b\"")
    public void whenTheCallerPairsDeviceD7b1ToPatient1f4b() {
        testContext.setLastResponse(
                performPair(TestJwtFactory.D7B1_ID, TestJwtFactory.PATIENT_1F4B_ID));
    }

    /** POST /api/v1/edge/devices/{E8C2_ID}/pairings with pacienteId = PATIENT_1F4B_ID. */
    @When("the caller pairs device \"e8c2\" to patient \"1f4b\"")
    public void whenTheCallerPairsDeviceE8c2ToPatient1f4b() {
        testContext.setLastResponse(
                performPair(TestJwtFactory.E8C2_ID, TestJwtFactory.PATIENT_1F4B_ID));
    }

    /** DELETE /api/v1/edge/devices/{D7B1_ID}/pairings/current with reason body. */
    @When("the caller unpairs device \"d7b1\" with reason \"device replacement\"")
    public void whenTheCallerUnpairsDeviceD7b1WithReasonDevice() {
        testContext.setLastResponse(performUnpair(TestJwtFactory.D7B1_ID, "device replacement"));
    }

    /** DELETE /api/v1/edge/devices/{D7B1_ID}/pairings/current with no body. */
    @When("the caller unpairs device \"d7b1\"")
    public void whenTheCallerUnpairsDeviceD7b1() {
        testContext.setLastResponse(performUnpair(TestJwtFactory.D7B1_ID, null));
    }

    /**
     * PAC uses edge.read authority to look up the paired device for patient "9c2a". The operator
     * token (set in Background via SharedSteps) has edge.read + edge.manage. GET
     * /api/v1/edge/patients/{PATIENT_9C2A_ID}/device
     */
    @When("PAC requests the device for patient \"9c2a\"")
    public void whenPacRequestsTheDeviceForPatient9c2a() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getOperatorToken());
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<Void> req = new HttpEntity<>(headers);
        String url = "/api/v1/edge/patients/" + TestJwtFactory.PATIENT_9C2A_ID + "/device";
        testContext.setLastResponse(restTemplate.exchange(url, HttpMethod.GET, req, String.class));
    }

    // -------------------------------------------------------------------------
    // Then / And — response assertions
    // -------------------------------------------------------------------------

    @And("the response body has pairingState \"paired\"")
    public void andTheResponseBodyHasPairingstatePaired() {
        try {
            String body = testContext.getLastResponse().getBody();
            assertThat(body).as("response body must not be null").isNotNull();
            JsonNode tree = MAPPER.readTree(body);
            JsonNode stateNode = tree.get("pairingState");
            assertThat(stateNode).as("pairingState field must be present").isNotNull();
            assertThat(stateNode.asText()).as("pairingState value").isEqualTo("paired");
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse response body for pairingState assertion: " + e.getMessage(), e);
        }
    }

    @And("the response body has pacienteId \"9c2a\"")
    public void andTheResponseBodyHasPacienteid9c2a() {
        try {
            String body = testContext.getLastResponse().getBody();
            assertThat(body).as("response body must not be null").isNotNull();
            JsonNode tree = MAPPER.readTree(body);
            JsonNode node = tree.get("pacienteId");
            assertThat(node).as("pacienteId field must be present in response").isNotNull();
            assertThat(node.asText())
                    .as("pacienteId must match PATIENT_9C2A_ID")
                    .isEqualTo(TestJwtFactory.PATIENT_9C2A_ID.toString());
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse response body for pacienteId assertion: " + e.getMessage(), e);
        }
    }

    @Then("the response status is 204")
    public void thenTheResponseStatusIs204() {
        assertThat(testContext.getLastResponse().getStatusCode().value())
                .as("HTTP status must be 204")
                .isEqualTo(204);
    }

    @And("device \"d7b1\" moves to state \"unpaired\"")
    public void andDeviceD7b1MovesToStateUnpaired() {
        String state =
                jdbcTemplate.queryForObject(
                        "SELECT pairing_state FROM t_emp_test.dispositivo_edge WHERE id = ?",
                        String.class,
                        TestJwtFactory.D7B1_ID);
        assertThat(state)
                .as("device d7b1 must be in 'unpaired' state after unpair")
                .isEqualTo("unpaired");
    }

    @And("the pacienteId of device \"d7b1\" is cleared")
    public void andThePacienteidOfDeviceD7b1IsCleared() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_emp_test.dispositivo_edge"
                                + " WHERE id = ? AND paciente_id IS NULL",
                        Integer.class,
                        TestJwtFactory.D7B1_ID);
        assertThat(count)
                .as("pacienteId must be NULL after unpair (cleared by the application)")
                .isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Transition audit assertions
    // -------------------------------------------------------------------------

    @And("a pairing transition \"registered -> paired\" is appended with the operator as actor")
    public void andAPairingTransitionRegisteredPairedIsAppendedWith() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_emp_test.dispositivo_edge_transition"
                                + " WHERE dispositivo_edge_id = ?"
                                + "   AND kind = 'pairing'"
                                + "   AND from_state = 'registered'"
                                + "   AND to_state = 'paired'",
                        Integer.class,
                        TestJwtFactory.D7B1_ID);
        assertThat(count)
                .as("a pairing transition registered -> paired must be recorded")
                .isGreaterThan(0);

        // actor = sub claim of the operator JWT, which is "operator-test"
        String actor =
                jdbcTemplate.queryForObject(
                        "SELECT actor FROM t_emp_test.dispositivo_edge_transition"
                                + " WHERE dispositivo_edge_id = ?"
                                + "   AND kind = 'pairing' AND to_state = 'paired'"
                                + " ORDER BY at DESC LIMIT 1",
                        String.class,
                        TestJwtFactory.D7B1_ID);
        assertThat(actor)
                .as("transition actor must be the operator's sub claim")
                .isEqualTo("operator-test");
    }

    @And(
            "a pairing transition \"paired -> unpaired\" is appended with reason \"device replacement\"")
    public void andAPairingTransitionPairedUnpairedIsAppendedWith() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_emp_test.dispositivo_edge_transition"
                                + " WHERE dispositivo_edge_id = ?"
                                + "   AND kind = 'pairing'"
                                + "   AND from_state = 'paired'"
                                + "   AND to_state = 'unpaired'"
                                + "   AND reason = 'device replacement'",
                        Integer.class,
                        TestJwtFactory.D7B1_ID);
        assertThat(count)
                .as(
                        "a pairing transition paired -> unpaired with reason 'device replacement' must be recorded")
                .isGreaterThan(0);
    }

    @And("a pairing transition \"unpaired -> paired\" is appended")
    public void andAPairingTransitionUnpairedPairedIsAppended() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_emp_test.dispositivo_edge_transition"
                                + " WHERE dispositivo_edge_id = ?"
                                + "   AND kind = 'pairing'"
                                + "   AND from_state = 'unpaired'"
                                + "   AND to_state = 'paired'",
                        Integer.class,
                        TestJwtFactory.E8C2_ID);
        assertThat(count)
                .as("a pairing transition unpaired -> paired must be recorded for e8c2")
                .isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // Error type assertions
    // -------------------------------------------------------------------------

    @And("the error type is \"\\/errors\\/pairing-conflict\"")
    public void andTheErrorTypeIsErrorspairingconflict() {
        assertErrorBodyContains("pairing-conflict");
    }

    @And("the error type is \"\\/errors\\/illegal-pairing-transition\"")
    public void andTheErrorTypeIsErrorsillegalpairingtransition() {
        assertErrorBodyContains("illegal-pairing-transition");
    }

    @And("the error type is \"\\/errors\\/patient-not-paired\"")
    public void andTheErrorTypeIsErrorspatientnotpaired() {
        assertErrorBodyContains("patient-not-paired");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void insertPairedDevice(
            java.util.UUID deviceId, java.util.UUID patientId, String label) {
        jdbcTemplate.update(
                "INSERT INTO t_emp_test.dispositivo_edge"
                        + " (id, tenant_id, paciente_id, pairing_state, label, row_version)"
                        + " VALUES (?, ?, ?, 'paired', ?, 0)",
                deviceId,
                TestJwtFactory.TEST_TENANT,
                patientId,
                label);
    }

    private ResponseEntity<String> performPair(java.util.UUID deviceId, java.util.UUID patientId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getOperatorToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Mirror PairingRequest exactly: pacienteId is the only field.
        String body = "{\"pacienteId\":\"" + patientId + "\"}";
        HttpEntity<String> req = new HttpEntity<>(body, headers);
        String url = "/api/v1/edge/devices/" + deviceId + "/pairings";
        return restTemplate.postForEntity(url, req, String.class);
    }

    private ResponseEntity<String> performUnpair(java.util.UUID deviceId, String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getOperatorToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> req;
        if (reason != null) {
            // Mirror UnpairRequest: reason is the only field, max 500 chars.
            String body = "{\"reason\":\"" + reason + "\"}";
            req = new HttpEntity<>(body, headers);
        } else {
            req = new HttpEntity<>(headers);
        }
        String url = "/api/v1/edge/devices/" + deviceId + "/pairings/current";
        return restTemplate.exchange(url, HttpMethod.DELETE, req, String.class);
    }

    private void assertErrorBodyContains(String errorCodeFragment) {
        try {
            String body = testContext.getLastResponse().getBody();
            assertThat(body).as("error response body must not be null").isNotNull();
            JsonNode tree = MAPPER.readTree(body);
            JsonNode typeNode = tree.get("type");
            assertThat(typeNode)
                    .as("'type' field must be present in ProblemDetail response")
                    .isNotNull();
            assertThat(typeNode.asText()).as("error type URI").contains(errorCodeFragment);
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse error response body for error type assertion: " + e.getMessage(),
                    e);
        }
    }
}
