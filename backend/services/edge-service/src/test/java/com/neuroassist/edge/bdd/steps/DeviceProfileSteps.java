package com.neuroassist.edge.bdd.steps;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.edge.bdd.CucumberSpringContext;
import com.neuroassist.edge.bdd.TestContext;
import com.neuroassist.edge.bdd.TestJwtFactory;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Step definitions for {@code device-profile.feature} (EDGE-022, P1).
 *
 * <p>WireMock stubs simulate GEST responses. The ACS service-token stub is pre-installed by {@link
 * com.neuroassist.edge.bdd.BddHooks#cleanUpAndResetStubs()} before each scenario.
 *
 * <p>DO NOT duplicate steps already defined in {@link SharedSteps}. Response-status assertions for
 * 200, 403 are in SharedSteps.
 */
public class DeviceProfileSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    /** URL pattern for GEST device-profile endpoint (any deviceId). */
    private static final String GEST_PROFILE_PATH = "/gest-service/detection-profile/devices/.*";

    private static final String PROFILE_ENDPOINT = "/api/v1/edge/profile";

    /**
     * Full profile JSON body mirroring {@code DetectionProfileResponse} from gest-service. The JSON
     * is copied from the actual GEST controller contract (field-by-field from
     * DetectionProfileResponse and DetectionProfilePrimitiveResponse). Keeping it as a constant
     * avoids divergence from the contract at the BDD layer — DO NOT invent field names.
     */
    private static final String FULL_PROFILE_JSON =
            "{"
                    + "\"id\":\"00000000-0000-0000-0000-000000000099\","
                    + "\"version\":7,"
                    + "\"active\":true,"
                    + "\"contractVersion\":\"1.0\","
                    + "\"thresholds\":{"
                    + "  \"baixa\":{\"earClose\":0.19,\"earOpen\":0.24,\"earOtherMax\":0.27,"
                    + "             \"blinkMinFrames\":3,\"doubleBlinkWindowMs\":1500,"
                    + "             \"briRaiseOn\":0.34,\"briRaiseOff\":0.26,\"briOtherMax\":0.29,\"browMinFrames\":7},"
                    + "  \"media\":{\"earClose\":0.22,\"earOpen\":0.27,\"earOtherMax\":0.30,"
                    + "             \"blinkMinFrames\":2,\"doubleBlinkWindowMs\":2000,"
                    + "             \"briRaiseOn\":0.30,\"briRaiseOff\":0.22,\"briOtherMax\":0.25,\"browMinFrames\":5},"
                    + "  \"alta\":{\"earClose\":0.25,\"earOpen\":0.30,\"earOtherMax\":0.33,"
                    + "            \"blinkMinFrames\":2,\"doubleBlinkWindowMs\":2500,"
                    + "            \"briRaiseOn\":0.26,\"briRaiseOff\":0.18,\"briOtherMax\":0.21,\"browMinFrames\":3}"
                    + "},"
                    + "\"primitives\":["
                    + "  {\"tipoGestoId\":\"11111111-1111-1111-1111-111111111111\","
                    + "   \"nome\":\"Piscar Simples\","
                    + "   \"engineFeatureRef\":\"blink_single\","
                    + "   \"sensibilidade\":\"media\","
                    + "   \"origem\":\"PERFIL_EMPRESA\"}"
                    + "],"
                    + "\"pacienteId\":\""
                    + TestJwtFactory.PATIENT_9C2A_ID.toString()
                    + "\","
                    + "\"catalogoVersao\":null,"
                    + "\"catalogoDegradado\":false,"
                    + "\"publishedAt\":\"2026-01-01T00:00:00Z\","
                    + "\"etag\":\"\\\"v7-9c2a\\\"\""
                    + "}";

    @Autowired private TestContext testContext;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    // -------------------------------------------------------------------------
    // GEST stub setup steps
    // -------------------------------------------------------------------------

    /** Stubs GEST to return the full profile (200) for any device. */
    @Given("GEST has a full detection profile for device {string}")
    public void givenGestHasAFullDetectionProfileForDevice(String deviceName) {
        CucumberSpringContext.WIRE_MOCK.stubFor(
                get(urlMatching(GEST_PROFILE_PATH))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withHeader("ETag", "\"v7-9c2a\"")
                                        .withHeader("Cache-Control", "max-age=60")
                                        .withBody(FULL_PROFILE_JSON)));
    }

    /**
     * Stubs GEST to return 304 for any request — used in the conditional GET scenario after the
     * first request has already captured the ETag.
     */
    @Given("GEST returns 304 for conditional GET from device {string}")
    public void givenGestReturns304ForConditionalGetFromDevice(String deviceName) {
        // Override the previous 200 stub: WireMock uses the last-added matching stub.
        CucumberSpringContext.WIRE_MOCK.stubFor(
                get(urlMatching(GEST_PROFILE_PATH)).willReturn(aResponse().withStatus(304)));
    }

    /**
     * Stubs GEST to return 503 (unavailable) and force-opens the {@code edge-device-profile}
     * circuit breaker, simulating a degraded GEST for the profile fetch path.
     */
    @Given("GEST is unavailable for profile requests from device {string}")
    public void givenGestIsUnavailableForProfileRequests(String deviceName) {
        CucumberSpringContext.WIRE_MOCK.stubFor(
                get(urlMatching(GEST_PROFILE_PATH)).willReturn(aResponse().withStatus(503)));
        circuitBreakerRegistry.circuitBreaker("edge-device-profile").transitionToOpenState();
    }

    // -------------------------------------------------------------------------
    // State-setup steps
    // -------------------------------------------------------------------------

    /**
     * Downgrades device "d7b1" from paired (inserted by Background) to the "registered" state by
     * UPDATE. A plain INSERT for "registered" already exists in {@link DevicePairingSteps}, but
     * that step does a blind INSERT which would fail with a duplicate-key error because the
     * Background already inserted d7b1 as "paired". This step uses UPDATE so it is safe after the
     * Background has run.
     */
    @Given("device {string} is downgraded to registered state")
    public void givenDeviceIsDowngradedToRegisteredState(String deviceName) {
        jdbcTemplate.update(
                "UPDATE t_emp_test.dispositivo_edge"
                        + " SET pairing_state = 'registered', paciente_id = NULL"
                        + " WHERE id = ?",
                TestJwtFactory.D7B1_ID);
    }

    // -------------------------------------------------------------------------
    // When steps
    // -------------------------------------------------------------------------

    /** Sends GET /api/v1/edge/profile with the device JWT (edge.device authority). */
    @When("device {string} requests the detection profile")
    public void whenDeviceRequestsTheDetectionProfile(String deviceName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getDeviceToken());
        ResponseEntity<String> response =
                restTemplate.exchange(
                        PROFILE_ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        testContext.setLastResponse(response);
    }

    /**
     * Makes the first profile request and captures the ETag from the response body's {@code etag}
     * field for use in the subsequent conditional GET step.
     */
    @And("device {string} requests the detection profile to capture the ETag")
    public void andDeviceRequestsTheDetectionProfileToCaptureEtag(String deviceName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getDeviceToken());
        ResponseEntity<String> response =
                restTemplate.exchange(
                        PROFILE_ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        testContext.setLastResponse(response);

        // Capture the ETag from the response body (field "etag" in DeviceProfileResponse).
        try {
            JsonNode tree = MAPPER.readTree(response.getBody());
            JsonNode etagNode = tree.get("etag");
            if (etagNode != null && !etagNode.isNull()) {
                testContext.setCapturedETag(etagNode.asText());
            }
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse profile response to capture ETag: " + e.getMessage(), e);
        }
    }

    /**
     * Sends GET /api/v1/edge/profile with the captured ETag in the {@code If-None-Match} header,
     * triggering a conditional GET.
     */
    @When("device {string} requests the profile with the captured ETag")
    public void whenDeviceRequestsTheProfileWithCapturedEtag(String deviceName) {
        String etag = testContext.getCapturedETag();
        assertThat(etag).as("capturedETag must be set by a previous step").isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getDeviceToken());
        headers.set(HttpHeaders.IF_NONE_MATCH, etag);
        ResponseEntity<String> response =
                restTemplate.exchange(
                        PROFILE_ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        testContext.setLastResponse(response);
    }

    /**
     * Sends GET /api/v1/edge/profile with an operator token (edge.read authority) to verify the
     * endpoint rejects non-device callers.
     */
    @When("an operator with role {string} requests the detection profile")
    public void whenAnOperatorRequestsTheDetectionProfile(String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testContext.getOperatorToken());
        ResponseEntity<String> response =
                restTemplate.exchange(
                        PROFILE_ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        testContext.setLastResponse(response);
    }

    // -------------------------------------------------------------------------
    // Then / And steps
    // -------------------------------------------------------------------------

    /** Asserts that the response body's {@code pacienteId} matches the expected patient. */
    @And("the profile response contains pacienteId for patient {string}")
    public void andTheProfileResponseContainsPacienteId(String patientName) {
        String expectedId =
                "9c2a".equals(patientName) ? TestJwtFactory.PATIENT_9C2A_ID.toString() : null;
        assertThat(expectedId)
                .as("patientName '%s' not mapped to a test UUID", patientName)
                .isNotNull();
        try {
            JsonNode tree = MAPPER.readTree(testContext.getLastResponse().getBody());
            JsonNode pacienteIdNode = tree.get("pacienteId");
            assertThat(pacienteIdNode).as("pacienteId field in response").isNotNull();
            assertThat(pacienteIdNode.asText()).isEqualTo(expectedId);
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse profile response for pacienteId: " + e.getMessage(), e);
        }
    }

    /** Asserts that the {@code primitives} array is non-empty. */
    @And("the profile response contains at least one primitive")
    public void andTheProfileResponseContainsAtLeastOnePrimitive() {
        try {
            JsonNode tree = MAPPER.readTree(testContext.getLastResponse().getBody());
            JsonNode primitives = tree.get("primitives");
            assertThat(primitives).as("primitives field in response").isNotNull();
            assertThat(primitives.isArray()).isTrue();
            assertThat(primitives.size()).as("primitives array must not be empty").isGreaterThan(0);
        } catch (Exception e) {
            throw new AssertionError(
                    "Cannot parse profile response for primitives: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts that the error body's {@code type} URI (RFC 7807 ProblemDetail) contains the given
     * code fragment. Uses the same helper pattern as {@link SharedSteps}.
     */
    @And("the error code contains {string}")
    public void andTheErrorCodeContains(String codeFragment) {
        try {
            String body = testContext.getLastResponse().getBody();
            assertThat(body).as("error response body must not be null").isNotNull();
            JsonNode tree = MAPPER.readTree(body);
            JsonNode typeNode = tree.get("type");
            assertThat(typeNode).as("'type' field in ProblemDetail").isNotNull();
            assertThat(typeNode.asText()).as("error type URI").contains(codeFragment);
        } catch (Exception e) {
            throw new AssertionError("Cannot parse error response body: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts that GEST unavailability produced an explicit error response (non-200) rather than a
     * 200 with an empty profile body. The "empty profile" failure mode makes the station appear
     * healthy while detecting nothing — the worst possible outcome for this product.
     */
    @Then("the response is an explicit error, not an empty profile")
    public void thenTheResponseIsAnExplicitError() {
        int status = testContext.getLastResponse().getStatusCode().value();
        assertThat(status)
                .as("GEST unavailability must NOT produce 200 — an empty profile is silent failure")
                .isNotEqualTo(200);

        // Additionally verify it is NOT an empty body (it should be a ProblemDetail).
        String body = testContext.getLastResponse().getBody();
        // 502 or 5xx with ProblemDetail body
        assertThat(body).as("error response body should not be blank").isNotBlank();
    }

    /** Asserts that the response status is not 200. */
    @And("the response status is not 200")
    public void andTheResponseStatusIsNot200() {
        assertThat(testContext.getLastResponse().getStatusCode().value())
                .as("response status must not be 200")
                .isNotEqualTo(200);
    }
}
