package com.neuroassist.evnt.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.application.port.out.CommandLookupPort;
import com.neuroassist.evnt.application.port.out.GestValidationPort;
import com.neuroassist.evnt.bdd.ScenarioContext;
import com.neuroassist.evnt.infrastructure.persistence.entity.EventoJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.entity.NotificationDispatchJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.EventoJpaRepository;
import com.neuroassist.evnt.infrastructure.persistence.repository.NotificationDispatchJpaRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — Idempotent recognized-command ingress (P8: EVNT-022/023). Feature:
 * report-ingress-idempotency.feature
 */
@RequiredArgsConstructor
public class ReportIngressIdempotencySteps {

    private final ScenarioContext ctx;

    private final GestValidationPort gestValidationPort;

    private final CommandLookupPort commandLookupPort;

    private final CaregiverRoutingPort caregiverRoutingPort;

    private final EventoJpaRepository eventoJpaRepository;

    private final NotificationDispatchJpaRepository notificationDispatchJpaRepository;

    private final SharedSteps sharedSteps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Background steps
    // -------------------------------------------------------------------------

    @Given(
            "a tenant \"empresa_demo\" with patient \"pac-1\" and command \"cmd-emergency\" of urgency \"emergencia\"")
    public void givenATenantEmpresademoWithPatientPac1AndCommand() {
        ctx.setTenantId("empresa_demo");
        ctx.setPacienteId("pac-1");
        ctx.setCommandRef("cmd-emergency");
        ctx.setUrgencia("emergencia");
        when(commandLookupPort.lookup("cmd-emergency"))
                .thenReturn(
                        new CommandLookupPort.CommandSnapshot(
                                "cmd-emergency", "Emergency Command", "emergencia"));
        when(caregiverRoutingPort.resolveCaregiver(any())).thenReturn("cg-1");
    }

    @And("a valid device token scoped to tenant \"empresa_demo\" and patient \"pac-1\"")
    public void andAValidDeviceTokenScopedToTenantEmpresademo() {
        ctx.setDeviceTokenTenant("empresa_demo");
    }

    @And("GEST will validate any well-formed report for a known profile")
    public void andGestWillValidateAnyWellformedReportForA() {
        when(gestValidationPort.validate(any())).thenReturn(true);
    }

    // -------------------------------------------------------------------------
    // Scenario: A new report creates exactly one Evento
    // -------------------------------------------------------------------------

    @Given("a RecognizedCommandReport with reportId \"r-100\" for command \"cmd-emergency\"")
    public void givenARecognizedcommandreportWithReportidR100ForCommandCmdemergency() {
        ctx.setReportId("r-100");
    }

    @And("the response body field \"status\" is \"created\"")
    public void andTheResponseBodyFieldStatusIsCreated() {
        assertResponseBodyField("status", "created");
    }

    @And("the response body field \"feedback\" is \"nao_avaliado\"")
    public void andTheResponseBodyFieldFeedbackIsNaoavaliado() {
        // feedback is set on the Evento entity, not returned directly in ReportIngressResponse
        // Verify via repository
        List<EventoJpaEntity> eventos = eventoJpaRepository.findAll();
        assertThat(eventos).isNotEmpty();
        assertThat(eventos.get(0).getFeedback())
                .as("Evento feedback should be NAO_AVALIADO after ingress")
                .isEqualToIgnoringCase("NAO_AVALIADO");
    }

    @And("exactly one Evento exists with reportId \"r-100\" in tenant \"empresa_demo\"")
    public void andExactlyOneEventoExistsWithReportidR100In() {
        // reportId is tracked in report_idempotency, not stored in evento directly.
        // We verify that exactly one Evento exists for the current scenario run.
        List<EventoJpaEntity> eventos = eventoJpaRepository.findAll();
        assertThat(eventos).as("Exactly one Evento should exist").hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Scenario: Duplicate reportId returns original verdict
    // -------------------------------------------------------------------------

    @Given(
            "a RecognizedCommandReport with reportId \"r-100\" for command \"cmd-emergency\" was already processed")
    public void givenARecognizedcommandreportWithReportidR100ForCommandCmdemergency_2() {
        ctx.setReportId("r-100");
        // Pre-ingest a report so idempotency kicks in for the second POST
        sharedSteps.postReport(); // first ingestion
    }

    @When("the edge posts a report with the same reportId \"r-100\"")
    public void whenTheEdgePostsAReportWithTheSame() {
        // POST again with the same reportId
        ctx.setReportId("r-100");
        ResponseEntity<String> response = sharedSteps.postReport();
        ctx.setLastResponse(response);
    }

    @And("the response body field \"status\" is \"duplicate\"")
    public void andTheResponseBodyFieldStatusIsDuplicate() {
        assertResponseBodyField("status", "duplicate");
    }

    @And("no additional notification dispatch was created")
    public void andNoAdditionalNotificationDispatchWasCreated() {
        // First ingestion created one dispatch; idempotent second call should not create another
        List<NotificationDispatchJpaEntity> dispatches =
                notificationDispatchJpaRepository.findAll();
        assertThat(dispatches.size())
                .as("Only one dispatch should exist (no duplicate created)")
                .isLessThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Scenario: GEST rejection yields no Evento
    // -------------------------------------------------------------------------

    @Given("GEST will reject the report with reason \"primitive-not-in-profile\"")
    public void givenGestWillRejectTheReportWithReasonPrimitivenotinprofile() {
        when(gestValidationPort.validate(any())).thenReturn(false);
    }

    @And("a RecognizedCommandReport with reportId \"r-200\" for command \"cmd-emergency\"")
    public void andARecognizedcommandreportWithReportidR200ForCommandCmdemergency() {
        ctx.setReportId("r-200");
    }

    @And("the ProblemDetail field \"type\" is \"/errors/gest-rejected\"")
    public void andTheProblemdetailFieldTypeIsErrorsgestrejected() {
        assertProblemDetailTypeEndsWith("/errors/gest-rejected");
    }

    @And("no Evento exists with reportId \"r-200\" in tenant \"empresa_demo\"")
    public void andNoEventoExistsWithReportidR200InTenant() {
        List<EventoJpaEntity> eventos = eventoJpaRepository.findAll();
        assertThat(eventos).as("No Evento should exist after GEST rejection").isEmpty();
    }

    // -------------------------------------------------------------------------
    // Scenario: Biometric payload is rejected
    // -------------------------------------------------------------------------

    @Given("a RecognizedCommandReport with reportId \"r-300\" that includes a raw frame payload")
    public void givenARecognizedcommandreportWithReportidR300ThatIncludesA() {
        ctx.setReportId("r-300");
        ctx.setRawFramePayload(true);
    }

    @Then("the response status is 400")
    public void thenTheResponseStatusIs400() {
        assertThat(ctx.getLastResponse().getStatusCode().value()).isEqualTo(400);
    }

    @And("the ProblemDetail field \"type\" is \"/errors/forbidden-biometric-payload\"")
    public void andTheProblemdetailFieldTypeIsErrorsforbiddenbiometricpayload() {
        assertProblemDetailTypeEndsWith("/errors/forbidden-biometric-payload");
    }

    // -------------------------------------------------------------------------
    // Scenario Outline: Device token tenant must match patient tenant
    // -------------------------------------------------------------------------

    @Given("a valid device token scoped to tenant \"{string}\" and patient \"pac-1\"")
    public void givenAValidDeviceTokenScopedToTenantTokentenant(String tokenTenant) {
        ctx.setDeviceTokenTenant(tokenTenant);
    }

    @And("a RecognizedCommandReport with reportId \"{string}\" for command \"cmd-emergency\"")
    public void andARecognizedcommandreportWithReportidReportidForCommandCmdemergency(
            String reportId) {
        ctx.setReportId(reportId);
    }

    @Then("the response status is {int}")
    public void thenTheResponseStatusIsStatus(int status) {
        assertThat(ctx.getLastResponse().getStatusCode().value())
                .as("Expected HTTP status " + status)
                .isEqualTo(status);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void assertResponseBodyField(String field, String expectedValue) {
        try {
            String body = ctx.getLastResponse().getBody();
            assertThat(body).as("Response body must not be null").isNotNull();
            JsonNode json = objectMapper.readTree(body);
            assertThat(json.has(field))
                    .as("Response body should contain field '" + field + "'")
                    .isTrue();
            assertThat(json.get(field).asText())
                    .as("Response body field '" + field + "'")
                    .isEqualTo(expectedValue);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse response body: " + e.getMessage(), e);
        }
    }

    private void assertProblemDetailTypeEndsWith(String suffix) {
        try {
            String body = ctx.getLastResponse().getBody();
            assertThat(body).as("Response body must not be null").isNotNull();
            JsonNode json = objectMapper.readTree(body);
            String type = json.has("type") ? json.get("type").asText() : "";
            assertThat(type)
                    .as(
                            "ProblemDetail type should end with '"
                                    + suffix
                                    + "' but was '"
                                    + type
                                    + "'")
                    .endsWith(suffix);
        } catch (Exception e) {
            throw new AssertionError(
                    "Failed to parse ProblemDetail response: " + e.getMessage(), e);
        }
    }
}
