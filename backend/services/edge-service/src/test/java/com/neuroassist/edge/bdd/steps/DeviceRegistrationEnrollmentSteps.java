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
import java.time.Instant;
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
 * Step definitions — Device registration and enrollment. Feature:
 * device-registration-enrollment.feature
 *
 * <p>Implemented in P13 (EDGE-019). Shared steps (common to multiple features) live in
 * SharedSteps.java.
 *
 * <p>All DB operations use fully qualified {@code t_emp_test.*} and {@code neuroassist_global.*}
 * table names because {@code JdbcTemplate} bypasses Hibernate's multi-tenancy and operates against
 * the raw DataSource.
 */
public class DeviceRegistrationEnrollmentSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Autowired private TestContext testContext;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private TestRestTemplate restTemplate;

    // -------------------------------------------------------------------------
    // Background
    // -------------------------------------------------------------------------

    /**
     * Verifies that the tenant schema {@code t_emp_test} was created by {@code init-schema.sql}
     * when the Testcontainers PostgreSQL container started. A documentation step — fails fast if
     * the init script was not executed.
     */
    @And("the tenant schema is provisioned")
    public void andTheTenantSchemaIsProvisioned() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.schemata"
                                + " WHERE schema_name = 't_emp_test'",
                        Integer.class);
        assertThat(count).as("t_emp_test schema must exist in the test database").isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    @When("the caller registers a device with label \"Camera quarto do Marcio\"")
    public void whenTheCallerRegistersADeviceWithLabelCamera() {
        registerDevice("Camera quarto do Marcio");
    }

    @And("the response body has pairingState \"registered\"")
    public void andTheResponseBodyHasPairingstateRegistered() {
        assertPairingStateEquals("registered");
    }

    @And("the response body contains an enrollmentToken")
    public void andTheResponseBodyContainsAnEnrollmenttoken() {
        try {
            JsonNode tree = MAPPER.readTree(testContext.getLastResponse().getBody());
            assertThat(tree.get("enrollmentToken"))
                    .as("enrollmentToken field must be present in registration response")
                    .isNotNull();
            assertThat(tree.get("enrollmentToken").asText())
                    .as("enrollmentToken must not be blank")
                    .isNotBlank();
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse response body for enrollmentToken assertion: " + e.getMessage(),
                    e);
        }
    }

    @And("the response body has an enrollmentExpiresAt within 15 minutes")
    public void andTheResponseBodyHasAnEnrollmentexpiresatWithin15() {
        try {
            JsonNode tree = MAPPER.readTree(testContext.getLastResponse().getBody());
            JsonNode expiresAtNode = tree.get("enrollmentExpiresAt");
            assertThat(expiresAtNode)
                    .as("enrollmentExpiresAt field must be present in registration response")
                    .isNotNull();
            Instant expiresAt = Instant.parse(expiresAtNode.asText());
            Instant now = Instant.now();
            assertThat(expiresAt).as("enrollmentExpiresAt must be in the future").isAfter(now);
            assertThat(expiresAt)
                    .as("enrollmentExpiresAt must be within 16 minutes")
                    .isBefore(now.plusSeconds(16 * 60));
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse response body for enrollmentExpiresAt assertion: "
                            + e.getMessage(),
                    e);
        }
    }

    @And("the enrollment token is stored only as a hash")
    public void andTheEnrollmentTokenIsStoredOnlyAsA() {
        UUID deviceId = testContext.getEnrolledDeviceId();
        assertThat(deviceId)
                .as("enrolledDeviceId must be set by the registration step")
                .isNotNull();
        String plainToken = testContext.getEnrollmentToken();
        assertThat(plainToken)
                .as("plaintext enrollment token must be stored in TestContext")
                .isNotBlank();

        String storedHash =
                jdbcTemplate.queryForObject(
                        "SELECT token_hash FROM t_emp_test.dispositivo_enrollment"
                                + " WHERE dispositivo_edge_id = ?",
                        String.class,
                        deviceId);
        assertThat(storedHash)
                .as("token_hash must be present in dispositivo_enrollment")
                .isNotNull()
                .isNotBlank();
        // SHA-256 hex = 64 characters
        assertThat(storedHash.length())
                .as("token_hash must be a 64-character SHA-256 hex string")
                .isEqualTo(64);
        assertThat(storedHash)
                .as("stored hash must not equal the plaintext token")
                .isNotEqualTo(plainToken);
    }

    // -------------------------------------------------------------------------
    // Scope guard (insufficient authority)
    // -------------------------------------------------------------------------

    /**
     * Sets the caller's JWT to a read-only token ({@code scope=edge.read}, no {@code edge.manage}).
     */
    @Given("the caller has role \"edge.read\"")
    public void givenTheCallerHasRoleEdgeread() {
        testContext.setOperatorToken(
                TestJwtFactory.createReadOnlyToken(TestJwtFactory.TEST_TENANT));
    }

    @When("the caller registers a device with label \"Camera sala\"")
    public void whenTheCallerRegistersADeviceWithLabelCamera_2() {
        registerDevice("Camera sala");
    }

    @And("the error type is \"\\/errors\\/insufficient-scope\"")
    public void andTheErrorTypeIsErrorsinsufficientscope() {
        assertErrorBodyContains("insufficient-scope");
    }

    // -------------------------------------------------------------------------
    // Enrollment — happy path
    // -------------------------------------------------------------------------

    /**
     * Registers a new device via the API and stores the plaintext enrollment token plus the device
     * UUID in {@link TestContext} for subsequent enrollment steps.
     */
    @Given("a device is registered with a valid enrollment token")
    public void givenADeviceIsRegisteredWithAValidEnrollment() {
        registerAndStoreDevice("valid-enrollment-device");
    }

    @When("the edge agent enrolls with that token and agentVersion \"1.4.2\"")
    public void whenTheEdgeAgentEnrollsWithThatTokenAnd() {
        enrollWithStoredToken("1.4.2");
    }

    @And("the response body contains a clientId and a clientSecret")
    public void andTheResponseBodyContainsAClientidAndA() {
        try {
            JsonNode tree = MAPPER.readTree(testContext.getLastResponse().getBody());
            assertThat(tree.get("clientId"))
                    .as("clientId must be present in enroll response")
                    .isNotNull();
            assertThat(tree.get("clientSecret"))
                    .as("clientSecret must be present in enroll response")
                    .isNotNull();
            assertThat(tree.get("clientId").asText()).as("clientId must not be blank").isNotBlank();
            assertThat(tree.get("clientSecret").asText())
                    .as("clientSecret must not be blank")
                    .isNotBlank();
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse enroll response for clientId/clientSecret assertion: "
                            + e.getMessage(),
                    e);
        }
    }

    /**
     * Verifies the clientSecret is returned only once by calling enroll a second time with the same
     * token and asserting the second attempt is rejected (401). The {@link
     * TestContext#lastResponse} is NOT updated so subsequent assertions still target the first
     * (successful) enroll response.
     */
    @And("the clientSecret is returned exactly once")
    public void andTheClientsecretIsReturnedExactlyOnce() {
        // testContext.lastResponse still holds the first successful enroll response.
        // Attempt a second enroll with the same token — the token was consumed by the first call.
        ResponseEntity<String> secondAttempt =
                performEnroll(testContext.getEnrollmentToken(), "1.0");
        assertThat(secondAttempt.getStatusCode().value())
                .as("second enroll with the same (consumed) token must be rejected with 401")
                .isEqualTo(401);
        // Do NOT update testContext.lastResponse — keep the first (successful) response for
        // subsequent assertions (credential hash check, active credential count).
    }

    @And("the credential is stored only as a hash")
    public void andTheCredentialIsStoredOnlyAsAHash() {
        UUID deviceId = testContext.getEnrolledDeviceId();
        try {
            // The first enroll response is still in lastResponse
            // (andTheClientsecretIsReturnedExactlyOnce
            // did not overwrite it).
            JsonNode tree = MAPPER.readTree(testContext.getLastResponse().getBody());
            String plainSecret = tree.get("clientSecret").asText();

            String storedHash =
                    jdbcTemplate.queryForObject(
                            "SELECT secret_hash FROM t_emp_test.dispositivo_credential"
                                    + " WHERE dispositivo_edge_id = ?",
                            String.class,
                            deviceId);
            assertThat(storedHash)
                    .as("secret_hash must be present in dispositivo_credential")
                    .isNotNull()
                    .isNotBlank();
            assertThat(storedHash.length())
                    .as("secret_hash must be a 64-character SHA-256 hex string")
                    .isEqualTo(64);
            assertThat(storedHash)
                    .as("stored hash must not equal the plaintext secret")
                    .isNotEqualTo(plainSecret);
        } catch (Exception e) {
            throw new AssertionError("Cannot verify credential hash: " + e.getMessage(), e);
        }
    }

    @And("exactly one active credential exists for the device")
    public void andExactlyOneActiveCredentialExistsForTheDevice() {
        UUID deviceId = testContext.getEnrolledDeviceId();
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_emp_test.dispositivo_credential"
                                + " WHERE dispositivo_edge_id = ? AND status = 'active'",
                        Integer.class,
                        deviceId);
        assertThat(count)
                .as("exactly one active credential must exist for the device after enrollment")
                .isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Enrollment — expired token
    // -------------------------------------------------------------------------

    @Given("a device is registered with an enrollment token that expired 1 minute ago")
    public void givenADeviceIsRegisteredWithAnEnrollmentToken() {
        registerAndStoreDevice("expired-token-device");
        // Expire the token: set expires_at to 2 minutes in the past
        jdbcTemplate.update(
                "UPDATE t_emp_test.dispositivo_enrollment"
                        + " SET expires_at = now() - INTERVAL '2 minutes'"
                        + " WHERE dispositivo_edge_id = ?",
                testContext.getEnrolledDeviceId());
    }

    @When("the edge agent enrolls with that token")
    public void whenTheEdgeAgentEnrollsWithThatToken() {
        testContext.setLastResponse(performEnroll(testContext.getEnrollmentToken(), "1.0"));
    }

    @Then("the response status is 401")
    public void thenTheResponseStatusIs401() {
        assertThat(testContext.getLastResponse().getStatusCode().value())
                .as("HTTP status must be 401")
                .isEqualTo(401);
    }

    @And("the error type is \"\\/errors\\/enrollment-invalid\"")
    public void andTheErrorTypeIsErrorsenrollmentinvalid() {
        assertErrorBodyContains("enrollment-invalid");
    }

    // -------------------------------------------------------------------------
    // Enrollment — token already consumed
    // -------------------------------------------------------------------------

    /**
     * Registers a device and performs the first enrollment (consuming the token). The plaintext
     * token is stored in {@link TestContext} for the second attempt in the When step.
     */
    @Given("a device has already completed enrollment with its token")
    public void givenADeviceHasAlreadyCompletedEnrollmentWithIts() {
        registerAndStoreDevice("token-already-consumed-device");
        // Perform first enroll — this consumes the token
        ResponseEntity<String> firstEnroll = performEnroll(testContext.getEnrollmentToken(), "1.0");
        assertThat(firstEnroll.getStatusCode().value())
                .as("first enroll must succeed with 200")
                .isEqualTo(200);
        // Do NOT clear enrollmentToken — the When step uses it for the second attempt
    }

    @When("the edge agent enrolls again with the same token")
    public void whenTheEdgeAgentEnrollsAgainWithTheSame() {
        testContext.setLastResponse(performEnroll(testContext.getEnrollmentToken(), "1.0"));
    }

    // -------------------------------------------------------------------------
    // Enrollment — already-enrolled device
    // -------------------------------------------------------------------------

    /**
     * Registers a device via the API, then updates its state to 'paired' directly in the DB to
     * simulate a device that has already completed enrollment and is in an active pairing. The
     * enrollment token is still present in the global directory and the tenant table (not
     * consumed), so the enroll flow reaches the {@code isEnrolled} check and throws 409.
     */
    @Given("a device is already enrolled and in state \"paired\"")
    public void givenADeviceIsAlreadyEnrolledAndInState() {
        registerAndStoreDevice("already-enrolled-device");
        // Directly update the device to 'paired' state (simulating a completed enrollment +
        // pairing).
        // The CHECK constraint requires paciente_id IS NOT NULL when pairing_state = 'paired'.
        jdbcTemplate.update(
                "UPDATE t_emp_test.dispositivo_edge"
                        + " SET pairing_state = 'paired', paciente_id = ?"
                        + " WHERE id = ?",
                TestJwtFactory.PATIENT_9C2A_ID,
                testContext.getEnrolledDeviceId());
    }

    @When("the edge agent enrolls that device again")
    public void whenTheEdgeAgentEnrollsThatDeviceAgain() {
        testContext.setLastResponse(performEnroll(testContext.getEnrollmentToken(), "1.0"));
    }

    @And("the error type is \"\\/errors\\/already-enrolled\"")
    public void andTheErrorTypeIsErrorsalreadyenrolled() {
        assertErrorBodyContains("already-enrolled");
    }

    // -------------------------------------------------------------------------
    // Retire
    // -------------------------------------------------------------------------

    /**
     * Inserts device "d7b1" in the 'paired' state with an active credential directly in the DB.
     * This simulates a fully enrolled + paired device without going through the API registration
     * and enrollment flow, which makes the retire scenario self-contained and fast.
     */
    @Given("a device \"d7b1\" is enrolled and paired")
    public void givenADeviceD7b1IsEnrolledAndPaired() {
        jdbcTemplate.update(
                "INSERT INTO t_emp_test.dispositivo_edge"
                        + " (id, tenant_id, paciente_id, pairing_state, label, row_version)"
                        + " VALUES (?, ?, ?, 'paired', 'test-device-d7b1-enrolled', 0)",
                TestJwtFactory.D7B1_ID,
                TestJwtFactory.TEST_TENANT,
                TestJwtFactory.PATIENT_9C2A_ID);
        jdbcTemplate.update(
                "INSERT INTO t_emp_test.dispositivo_credential"
                        + " (id, tenant_id, dispositivo_edge_id, client_id, secret_hash, status, issued_at)"
                        + " VALUES (gen_random_uuid(), ?, ?, ?, ?, 'active', now())",
                TestJwtFactory.TEST_TENANT,
                TestJwtFactory.D7B1_ID,
                "cred-d7b1-retire-test",
                "hash-d7b1-retire-test-placeholder");
    }

    @When("the caller retires device \"d7b1\"")
    public void whenTheCallerRetiresDeviceD7b1() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getOperatorToken());
        HttpEntity<Void> req = new HttpEntity<>(headers);
        String url = "/api/v1/edge/devices/" + TestJwtFactory.D7B1_ID + ":retire";
        testContext.setLastResponse(restTemplate.postForEntity(url, req, String.class));
    }

    /**
     * Verifies pairingState after retire by doing a follow-up GET. The retire endpoint returns 200
     * with NO body ({@code ResponseEntity.ok().build()}); this step queries the current state
     * through the device-read endpoint.
     */
    @And("the response body has pairingState \"retired\"")
    public void andTheResponseBodyHasPairingstateRetired() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getOperatorToken());
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<Void> req = new HttpEntity<>(headers);
        ResponseEntity<String> getResponse =
                restTemplate.exchange(
                        "/api/v1/edge/devices/" + TestJwtFactory.D7B1_ID,
                        HttpMethod.GET,
                        req,
                        String.class);
        assertThat(getResponse.getStatusCode().value())
                .as("GET device after retire must return 200")
                .isEqualTo(200);
        try {
            JsonNode tree = MAPPER.readTree(getResponse.getBody());
            assertThat(tree.get("pairingState").asText())
                    .as("pairingState must be 'retired' after retire call")
                    .isEqualTo("retired");
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse GET device response for pairingState assertion: "
                            + e.getMessage(),
                    e);
        }
    }

    @And("the device credential status is \"revoked\"")
    public void andTheDeviceCredentialStatusIsRevoked() {
        Integer activeCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_emp_test.dispositivo_credential"
                                + " WHERE dispositivo_edge_id = ? AND status = 'active'",
                        Integer.class,
                        TestJwtFactory.D7B1_ID);
        assertThat(activeCount)
                .as("no active credential must remain after device retirement")
                .isEqualTo(0);

        Integer revokedCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_emp_test.dispositivo_credential"
                                + " WHERE dispositivo_edge_id = ? AND status = 'revoked'",
                        Integer.class,
                        TestJwtFactory.D7B1_ID);
        assertThat(revokedCount)
                .as("at least one revoked credential must exist after device retirement")
                .isGreaterThan(0);
    }

    @And("the device row is not deleted")
    public void andTheDeviceRowIsNotDeleted() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_emp_test.dispositivo_edge WHERE id = ?",
                        Integer.class,
                        TestJwtFactory.D7B1_ID);
        assertThat(count)
                .as(
                        "device row must still exist after retirement (retire is terminal, not a delete)")
                .isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Registers a device with the given label, stores the result in {@link TestContext}, and sets
     * {@link TestContext#lastResponse}. Requires the operator token to be already set (by
     * Background or a Given step).
     */
    private void registerDevice(String label) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getOperatorToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Mirror DeviceRegistrationRequest exactly: label is the only required field.
        String body = "{\"label\":\"" + label + "\"}";
        HttpEntity<String> req = new HttpEntity<>(body, headers);
        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/v1/edge/devices", req, String.class);
        testContext.setLastResponse(response);
        // Store device ID and enrollment token for subsequent steps (enrollment, DB assertions).
        if (response.getStatusCode().value() == 201 && response.getBody() != null) {
            try {
                JsonNode tree = MAPPER.readTree(response.getBody());
                testContext.setEnrolledDeviceId(UUID.fromString(tree.get("deviceId").asText()));
                testContext.setEnrollmentToken(tree.get("enrollmentToken").asText());
            } catch (Exception e) {
                throw new AssertionError(
                        "Cannot parse registration response to extract deviceId/enrollmentToken: "
                                + e.getMessage(),
                        e);
            }
        }
    }

    /**
     * Registers a device via the API and stores the result. Intended for Given steps that only need
     * the device + token without asserting anything about the registration response.
     */
    private void registerAndStoreDevice(String label) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getOperatorToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"label\":\"" + label + "\"}";
        HttpEntity<String> req = new HttpEntity<>(body, headers);
        ResponseEntity<String> regResponse =
                restTemplate.postForEntity("/api/v1/edge/devices", req, String.class);
        assertThat(regResponse.getStatusCode().value())
                .as("device registration in Given step must succeed with 201")
                .isEqualTo(201);
        try {
            JsonNode tree = MAPPER.readTree(regResponse.getBody());
            testContext.setEnrolledDeviceId(UUID.fromString(tree.get("deviceId").asText()));
            testContext.setEnrollmentToken(tree.get("enrollmentToken").asText());
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse registration response in Given step: " + e.getMessage(), e);
        }
    }

    /**
     * Calls POST /api/v1/edge/devices/enroll with the given token and agentVersion. No Bearer JWT —
     * this endpoint is permitAll (guarded by single-use token). Returns the raw ResponseEntity
     * without storing it in TestContext so callers can decide whether to store it.
     */
    private ResponseEntity<String> performEnroll(String enrollmentToken, String agentVersion) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Mirror EnrollRequest exactly: enrollmentToken + agentVersion, both @NotBlank.
        String body =
                "{\"enrollmentToken\":\""
                        + enrollmentToken
                        + "\",\"agentVersion\":\""
                        + agentVersion
                        + "\"}";
        HttpEntity<String> req = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity("/api/v1/edge/devices/enroll", req, String.class);
    }

    private void enrollWithStoredToken(String agentVersion) {
        ResponseEntity<String> response =
                performEnroll(testContext.getEnrollmentToken(), agentVersion);
        testContext.setLastResponse(response);
    }

    private void assertPairingStateEquals(String expected) {
        try {
            String body = testContext.getLastResponse().getBody();
            assertThat(body).as("response body must not be null").isNotNull();
            JsonNode tree = MAPPER.readTree(body);
            JsonNode stateNode = tree.get("pairingState");
            assertThat(stateNode).as("pairingState field in response").isNotNull();
            assertThat(stateNode.asText()).as("pairingState value").isEqualTo(expected);
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse response body for pairingState assertion: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts that the response body's {@code type} field (RFC 7807 ProblemDetail) contains the
     * given error code fragment. Uses {@code contains} because {@code ProblemDetailFactory}
     * prefixes codes with {@code "edge."} and the full type URI is {@code
     * https://neuroassist-ai.com/errors/edge.<code>}.
     */
    private void assertErrorBodyContains(String errorCodeFragment) {
        try {
            String body = testContext.getLastResponse().getBody();
            assertThat(body).as("error response body must not be null").isNotNull();
            JsonNode tree = MAPPER.readTree(body);
            JsonNode typeNode = tree.get("type");
            assertThat(typeNode).as("'type' field in ProblemDetail response").isNotNull();
            assertThat(typeNode.asText()).as("error type URI").contains(errorCodeFragment);
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse error response body for error type assertion: " + e.getMessage(),
                    e);
        }
    }
}
