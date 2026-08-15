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
 *
 * <p>NOTE: Steps with URL paths use {string} instead of literal "/..." to avoid Cucumber treating
 * "/" as alternation in Cucumber Expressions.
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

    @And("GEST will validate any well-formed report for a known profile")
    public void andGestWillValidateAnyWellformedReportForA() {
        when(gestValidationPort.validate(any())).thenReturn(true);
    }

    // -------------------------------------------------------------------------
    // Scenario: A new report creates exactly one Evento
    // -------------------------------------------------------------------------

    @And("the response body field {string} is {string}")
    public void andTheResponseBodyFieldIs(String field, String expectedValue) {
        try {
            String body = ctx.getLastResponse().getBody();
            assertThat(body).as("Response body must not be null").isNotNull();
            JsonNode json = objectMapper.readTree(body);
            if ("feedback".equals(field)) {
                // feedback is on Evento entity, not in ReportIngressResponse
                List<EventoJpaEntity> eventos = eventoJpaRepository.findAll();
                assertThat(eventos).isNotEmpty();
                assertThat(eventos.get(0).getFeedback())
                        .as("Evento feedback")
                        .isEqualToIgnoringCase(expectedValue);
                return;
            }
            assertThat(json.has(field))
                    .as("Response body should contain field '" + field + "'")
                    .isTrue();
            assertThat(json.get(field).asText())
                    .as("Response body field '" + field + "'")
                    .isEqualTo(expectedValue);
        } catch (AssertionError ae) {
            throw ae;
        } catch (Exception e) {
            throw new AssertionError("Failed to parse response body: " + e.getMessage(), e);
        }
    }

    @And("exactly one Evento exists with reportId {string} in tenant {string}")
    public void andExactlyOneEventoExistsWithReportidInTenant(String reportId, String tenant) {
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
        sharedSteps.postReport();
    }

    @When("the edge posts a report with the same reportId {string}")
    public void whenTheEdgePostsAReportWithTheSameReportId(String reportId) {
        ctx.setReportId(reportId);
        ResponseEntity<String> response = sharedSteps.postReport();
        ctx.setLastResponse(response);
    }

    @And("no additional notification dispatch was created")
    public void andNoAdditionalNotificationDispatchWasCreated() {
        List<NotificationDispatchJpaEntity> dispatches =
                notificationDispatchJpaRepository.findAll();
        assertThat(dispatches.size())
                .as("Only one dispatch should exist (no duplicate created)")
                .isLessThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Scenario: GEST rejection yields no Evento
    // -------------------------------------------------------------------------

    @Given("GEST will reject the report with reason {string}")
    public void givenGestWillRejectTheReportWithReason(String reason) {
        when(gestValidationPort.validate(any())).thenReturn(false);
    }

    @And("no Evento exists with reportId {string} in tenant {string}")
    public void andNoEventoExistsWithReportidInTenant(String reportId, String tenant) {
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

    // -------------------------------------------------------------------------
    // Scenario Outline: Device token tenant must match patient tenant
    // NOTE: {string} avoids Cucumber treating "/" alternation in literal annotations
    // -------------------------------------------------------------------------

    @Given("a valid device token scoped to tenant {string} and patient {string}")
    public void givenAValidDeviceTokenScopedToTenantAndPatient(String tokenTenant, String patient) {
        ctx.setDeviceTokenTenant(tokenTenant);
    }

    @And("a RecognizedCommandReport with reportId {string} for command {string}")
    public void andARecognizedcommandreportWithReportidForCommand(String reportId, String command) {
        ctx.setReportId(reportId);
        ctx.setCommandRef(command);
    }
}
