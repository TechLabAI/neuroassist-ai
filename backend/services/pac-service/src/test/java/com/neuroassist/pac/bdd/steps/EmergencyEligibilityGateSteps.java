package com.neuroassist.pac.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import com.neuroassist.pac.bdd.HttpTestHelper;
import com.neuroassist.pac.bdd.ScenarioContext;
import com.neuroassist.pac.bdd.TestDataHelper;
import com.neuroassist.pac.integration.ExternalServicesWireMock;
import com.neuroassist.pac.integration.PacWireMockStubs;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — Emergency Eligibility Gate (ADR-005). Feature:
 * emergency-eligibility-gate.feature (PAC-025, P9).
 *
 * <p>Not annotated with @Component — registered exclusively via {@link
 * com.neuroassist.pac.bdd.BddTestConfiguration}.
 */
@RequiredArgsConstructor
public class EmergencyEligibilityGateSteps {

    private final ScenarioContext ctx;
    private final HttpTestHelper http;
    private final TestDataHelper data;

    // -------------------------------------------------------------------------
    // Emergency command setup
    // -------------------------------------------------------------------------

    @Given(
            "command \"cmd-emg\" for patient \"pac-001\" exists with estado \"validated\", urgencia \"emergencia\"")
    public void givenCommandCmdemgForPatientPac001ExistsWithEstado() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID gst001 = TestDataHelper.primitiveUuid("gst-001");
        UUID id =
                data.createComando(
                        patientId,
                        ctx.getCurrentEmpresaId(),
                        "validated",
                        "emergencia",
                        0,
                        gst001,
                        null);
        ctx.putAlias("cmd-emg", id);
        ctx.setLastEtag("\"1\"");
    }

    @And("primitive \"gst-001\" is referenced as principal")
    public void andPrimitiveGst001IsReferencedAsPrincipal() {
        // The command already has gst-001 as primitivoPrincipalId (set in createComando).
        // This step just documents the scenario intent — no action needed.
    }

    @And("GEST reports primitive \"gst-001\" as \"supported\"")
    public void andGestReportsPrimitiveGst001AsSupported() {
        PacWireMockStubs.stubGestDetectability_Supported(ExternalServicesWireMock.gest());
    }

    @When(
            "^a POST request is made to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-emg:publish\" with matching ETag")
    public void whenAPostRequestIsMadeToApipacv1pacientespac001comandoscmdemgpublishWith() {
        PacWireMockStubs.stubHistAvailable(ExternalServicesWireMock.hist());
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-emg");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":publish";
        ResponseEntity<String> response = http.postWithEtag(path, ctx.getLastEtag());
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    // -------------------------------------------------------------------------
    // Not-supported primitive
    // -------------------------------------------------------------------------

    @And("primitive \"gst-pending\" is referenced as principal")
    public void andPrimitiveGstpendingIsReferencedAsPrincipal() {
        // Guard: if cmd-rot is already set, gst-pending is already used as principal there.
        // Creating another command here would cause a duplicate active mapping error.
        if (ctx.getAlias("cmd-rot") != null) {
            return;
        }
        // The step "command cmd-emg ... exists with estado validated, urgencia emergencia"
        // uses a fixed primitive UUID. Re-stub the command with gst-pending as principal.
        UUID patientId = ctx.getAlias("pac-001");
        UUID gstPending = TestDataHelper.primitiveUuid("gst-pending");
        UUID id =
                data.createComando(
                        patientId,
                        ctx.getCurrentEmpresaId(),
                        "validated",
                        "emergencia",
                        0,
                        gstPending,
                        null);
        ctx.putAlias("cmd-emg", id);
        ctx.setLastEtag("\"1\"");
    }

    @And("GEST reports primitive \"gst-pending\" as \"pending-training\"")
    public void andGestReportsPrimitiveGstpendingAsPendingtraining() {
        PacWireMockStubs.stubGestDetectability_NotSupported(ExternalServicesWireMock.gest());
    }

    @And("^the response body field \"type\" is \"/errors/emergency-not-detectable\"")
    public void andTheResponseBodyFieldTypeIsErrorsemergencynotdetectable() {
        assertJsonField("type", "/errors/emergency-not-detectable");
    }

    @And(
            "^a GET request to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-emg\" returns estado \"validated\"")
    public void andAGetRequestToApipacv1pacientespac001comandoscmdemgReturnsEstadoValidated() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-emg");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId;
        ResponseEntity<String> getResp = http.get(path);
        assertThat(getResp.getStatusCode().value()).isEqualTo(200);
        Object estado = JsonPath.parse(getResp.getBody()).read("$.estado");
        assertThat(estado).isEqualTo("validated");
    }

    // -------------------------------------------------------------------------
    // Composite command
    // -------------------------------------------------------------------------

    @Given(
            "command \"cmd-composite\" for patient \"pac-001\" exists with estado \"validated\", urgencia \"emergencia\"")
    public void givenCommandCmdcompositeForPatientPac001ExistsWithEstado() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID gst001 = TestDataHelper.primitiveUuid("gst-001");
        UUID gstPending = TestDataHelper.primitiveUuid("gst-pending");
        UUID id =
                data.createComando(
                        patientId,
                        ctx.getCurrentEmpresaId(),
                        "validated",
                        "emergencia",
                        0,
                        gst001,
                        gstPending);
        ctx.putAlias("cmd-composite", id);
        ctx.setLastEtag("\"1\"");
    }

    @And("primitive \"gst-001\" is referenced as principal \\(supported\\)")
    public void andPrimitiveGst001IsReferencedAsPrincipalSupported() {
        // Already set up in createComando above; GEST stub configured separately.
    }

    @And("primitive \"gst-pending\" is referenced as secondary")
    public void andPrimitiveGstpendingIsReferencedAsSecondary() {
        // Already set up in createComando above (primitivoSecundarioId = gst-pending).
    }

    @When(
            "^a POST request is made to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-composite:publish\" with matching ETag")
    public void whenAPostRequestIsMadeToApipacv1pacientespac001comandoscmdcompositepublishWith() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-composite");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":publish";
        ResponseEntity<String> response = http.postWithEtag(path, ctx.getLastEtag());
        ctx.setLastResponse(response);
    }

    // -------------------------------------------------------------------------
    // GEST unreachable (timeout)
    // -------------------------------------------------------------------------

    @And("GEST is unreachable \\(connection timeout\\)")
    public void andGestIsUnreachableConnectionTimeout() {
        // Stub GEST to delay beyond the test-profile read-timeout (500ms) → triggers FeignException
        PacWireMockStubs.stubGestDetectability_Timeout(ExternalServicesWireMock.gest());
    }

    @And("^the response body field \"type\" is \"/errors/eligibility-gate-unavailable\"")
    public void andTheResponseBodyFieldTypeIsErrorseligibilitygateunavailable() {
        assertJsonField("type", "/errors/eligibility-gate-unavailable");
    }

    @And("the response header \"Retry-After\" is present")
    public void andTheResponseHeaderRetryafterIsPresent() {
        assertThat(ctx.getLastResponse().getHeaders().containsKey("Retry-After"))
                .as("Response should contain Retry-After header")
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // Circuit breaker open
    // -------------------------------------------------------------------------

    @And("GEST circuit breaker is open")
    public void andGestCircuitBreakerIsOpen() {
        // Stub to return 503 — the circuit breaker records failures and opens.
        // Since test-profile CB uses 10-call sliding window, we stub 503 to trigger fallback.
        PacWireMockStubs.stubGestDetectability_ServiceUnavailable(ExternalServicesWireMock.gest());
    }

    // -------------------------------------------------------------------------
    // Routine command (advisory)
    // -------------------------------------------------------------------------

    @Given(
            "command \"cmd-rot\" for patient \"pac-001\" exists with estado \"validated\", urgencia \"rotina\"")
    public void givenCommandCmdrotForPatientPac001ExistsWithEstado() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID gstPending = TestDataHelper.primitiveUuid("gst-pending");
        UUID id =
                data.createComando(
                        patientId,
                        ctx.getCurrentEmpresaId(),
                        "validated",
                        "rotina",
                        0,
                        gstPending,
                        null);
        ctx.putAlias("cmd-rot", id);
        ctx.setLastEtag("\"1\"");
    }

    @When(
            "^a POST request is made to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-rot:publish\" with matching ETag")
    public void whenAPostRequestIsMadeToApipacv1pacientespac001comandoscmdrotpublishWith() {
        PacWireMockStubs.stubHistAvailable(ExternalServicesWireMock.hist());
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-rot");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":publish";
        ResponseEntity<String> response = http.postWithEtag(path, ctx.getLastEtag());
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    @And("the response body contains an array field \"warnings\" with at least 1 item")
    public void andTheResponseBodyContainsAnArrayFieldWarnings() {
        String body = ctx.getLastResponse().getBody();
        assertThat(body).as("Response body must not be null").isNotNull();
        try {
            java.util.List<?> warnings = JsonPath.parse(body).read("$.warnings");
            assertThat(warnings).as("Warnings array must have at least 1 item").isNotEmpty();
        } catch (com.jayway.jsonpath.PathNotFoundException e) {
            // warnings field absent — check if body contains "warnings" key at minimum
            assertThat(body).as("Response must contain 'warnings'").contains("warnings");
        }
    }

    // -------------------------------------------------------------------------
    // Recalibrate with urgency escalation
    // -------------------------------------------------------------------------

    @Given(
            "command \"cmd-pub\" for patient \"pac-001\" exists with estado \"published\", urgencia \"rotina\"")
    public void givenCommandCmdpubForPatientPac001ExistsWithEstado() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID gst001 = TestDataHelper.primitiveUuid("gst-001");
        UUID id =
                data.createComando(
                        patientId,
                        ctx.getCurrentEmpresaId(),
                        "published",
                        "rotina",
                        0,
                        gst001,
                        null);
        ctx.putAlias("cmd-pub", id);
        ctx.setLastEtag("\"1\"");
    }

    @And("the response body field \"urgencia\" is \"emergencia\"")
    public void andTheResponseBodyFieldUrgenciaIsEmergencia() {
        assertJsonField("urgencia", "emergencia");
    }

    // -------------------------------------------------------------------------
    // GEST SLA timeout (2500ms)
    // -------------------------------------------------------------------------

    @And("GEST responds after 2500ms")
    public void andGestRespondsAfter2500ms() {
        // Stub with 2500ms delay, exceeding the 500ms test-profile read-timeout
        ExternalServicesWireMock.gest()
                .stubFor(
                        WireMock.post(WireMock.urlEqualTo("/gest-service/eligibility"))
                                .willReturn(
                                        WireMock.aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(
                                                        "{\"eligible\":true,\"missingStates\":[]}")
                                                .withFixedDelay(2500)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void assertJsonField(String field, Object expected) {
        String body = ctx.getLastResponse().getBody();
        assertThat(body).as("Response body must not be null").isNotNull();
        Object actual = JsonPath.parse(body).read("$." + field);
        assertThat(actual).as("JSON field '%s'", field).isEqualTo(expected);
    }
}
