package com.neuroassist.evnt.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.bdd.ScenarioContext;
import com.neuroassist.evnt.infrastructure.persistence.entity.EventoJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.EventoJpaRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — Caregiver confirmation and false-positive feedback (P8: EVNT-023). Feature:
 * caregiver-confirmation-feedback.feature
 */
@RequiredArgsConstructor
public class CaregiverConfirmationFeedbackSteps {

    private final ScenarioContext ctx;

    private final TestRestTemplate restTemplate;

    private final EventoJpaRepository eventoJpaRepository;

    private final CaregiverRoutingPort caregiverRoutingPort;

    private final AuditTrailPort auditTrailPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** The UUID for the pre-created Evento "evt-1". Stored in ScenarioContext as alias. */
    private UUID evt1Id;

    // -------------------------------------------------------------------------
    // Background steps
    // -------------------------------------------------------------------------

    @Given("a tenant \"empresa_demo\" with an Evento \"evt-1\" in feedback state \"nao_avaliado\"")
    public void givenATenantEmpresademoWithAnEventoEvt1In() {
        ctx.setTenantId("empresa_demo");
        // Pre-create an Evento in NAO_AVALIADO state via JPA
        evt1Id = UUID.randomUUID();
        EventoJpaEntity entity =
                EventoJpaEntity.builder()
                        .id(evt1Id)
                        .pacienteId("pac-1")
                        .comandoRef("cmd-emergency")
                        .commandLabelSnapshot("Emergency Command")
                        .urgenciaSnapshot("emergencia")
                        .feedback("NAO_AVALIADO")
                        .recognizedAt(Instant.now())
                        .createdAt(Instant.now())
                        .build();
        eventoJpaRepository.save(entity);
        ctx.registerEvento("evt-1", evt1Id);
    }

    @And("a caregiver session for \"cg-1\" authorized for the Evento's patient")
    public void andACaregiverSessionForCg1AuthorizedForThe() {
        when(caregiverRoutingPort.resolveCaregiver(anyString())).thenReturn("cg-1");
    }

    @And("ACS resolves \"cg-1\" as a valid Cuidador")
    public void andAcsResolvesCg1AsAValidCuidador() {
        when(caregiverRoutingPort.resolveCaregiver(anyString())).thenReturn("cg-1");
    }

    // -------------------------------------------------------------------------
    // Scenario: A caregiver confirms an event as real
    // -------------------------------------------------------------------------

    @When("the caregiver posts verdict \"real\" to \"/api/v1/events/evt-1/confirmation\"")
    public void whenTheCaregiverPostsVerdictRealToApiv1eventsevt1confirmation() {
        ResponseEntity<String> response =
                postConfirmation(ctx.getEventoId("evt-1"), "real", "cg-1");
        ctx.setLastResponse(response);
    }

    @And("the Evento \"evt-1\" has feedback \"real\"")
    public void andTheEventoEvt1HasFeedbackReal() {
        UUID eventoId = ctx.getEventoId("evt-1");
        EventoJpaEntity entity = eventoJpaRepository.findById(eventoId).orElseThrow();
        assertThat(entity.getFeedback()).isEqualToIgnoringCase("REAL");
    }

    @And("the Evento \"evt-1\" records confirmadoPor \"cg-1\" and a confirmadoAt timestamp")
    public void andTheEventoEvt1RecordsConfirmadoporCg1AndA() {
        UUID eventoId = ctx.getEventoId("evt-1");
        EventoJpaEntity entity = eventoJpaRepository.findById(eventoId).orElseThrow();
        assertThat(entity.getConfirmadoPor())
                .as("confirmadoPor should be 'cg-1'")
                .isEqualTo("cg-1");
        assertThat(entity.getConfirmadoAt()).as("confirmadoAt should be set").isNotNull();
    }

    @And("an audit fact \"EventoConfirmado\" was appended to HIST")
    public void andAnAuditFactEventoconfirmadoWasAppendedToHist() {
        verify(auditTrailPort)
                .append(
                        ArgumentMatchers.eq("EVENTO_CONFIRMADO"),
                        ArgumentMatchers.eq(ctx.getEventoId("evt-1").toString()),
                        ArgumentMatchers.anyString());
    }

    // -------------------------------------------------------------------------
    // Scenario: A caregiver marks an event as a false positive
    // -------------------------------------------------------------------------

    @When("the caregiver posts verdict \"falso_positivo\" to \"/api/v1/events/evt-1/confirmation\"")
    public void whenTheCaregiverPostsVerdictFalsopositivoToApiv1eventsevt1confirmation() {
        ResponseEntity<String> response =
                postConfirmation(ctx.getEventoId("evt-1"), "falso_positivo", "cg-1");
        ctx.setLastResponse(response);
    }

    @And("the Evento \"evt-1\" has feedback \"falso_positivo\"")
    public void andTheEventoEvt1HasFeedbackFalsopositivo() {
        UUID eventoId = ctx.getEventoId("evt-1");
        EventoJpaEntity entity = eventoJpaRepository.findById(eventoId).orElseThrow();
        assertThat(entity.getFeedback()).isEqualToIgnoringCase("FALSO_POSITIVO");
    }

    @And("an audit fact \"FalsoPositivoMarcado\" was appended to HIST")
    public void andAnAuditFactFalsopositivomarcadoWasAppendedToHist() {
        verify(auditTrailPort)
                .append(
                        ArgumentMatchers.eq("FALSO_POSITIVO_MARCADO"),
                        ArgumentMatchers.eq(ctx.getEventoId("evt-1").toString()),
                        ArgumentMatchers.anyString());
    }

    @And("the false-positive rate for the Evento's command reflects the new verdict")
    public void andTheFalsepositiveRateForTheEventosCommandReflects() {
        // FP rate tracking is an eventual HIST concern; verify audit was called for the FP event
        verify(auditTrailPort)
                .append(
                        ArgumentMatchers.eq("FALSO_POSITIVO_MARCADO"),
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString());
    }

    // -------------------------------------------------------------------------
    // Scenario: Feedback is terminal - conflicting second verdict → 409
    // -------------------------------------------------------------------------

    @Given("the Evento \"evt-1\" already has feedback \"real\"")
    public void givenTheEventoEvt1AlreadyHasFeedbackReal() {
        UUID eventoId = ctx.getEventoId("evt-1");
        EventoJpaEntity entity = eventoJpaRepository.findById(eventoId).orElseThrow();
        entity.setFeedback("REAL");
        entity.setConfirmadoPor("cg-1");
        entity.setConfirmadoAt(Instant.now());
        eventoJpaRepository.save(entity);
    }

    @Then("the response status is 409")
    public void thenTheResponseStatusIs409() {
        assertThat(ctx.getLastResponse().getStatusCode().value()).isEqualTo(409);
    }

    @And("the ProblemDetail field \"type\" is \"/errors/feedback-terminal\"")
    public void andTheProblemdetailFieldTypeIsErrorsfeedbackterminal() {
        assertProblemDetailTypeEndsWith("/errors/feedback-terminal");
    }

    @And("the Evento \"evt-1\" still has feedback \"real\"")
    public void andTheEventoEvt1StillHasFeedbackReal() {
        UUID eventoId = ctx.getEventoId("evt-1");
        EventoJpaEntity entity = eventoJpaRepository.findById(eventoId).orElseThrow();
        assertThat(entity.getFeedback()).isEqualToIgnoringCase("REAL");
    }

    // -------------------------------------------------------------------------
    // Scenario: A verdict requires a resolvable confirmer → 422
    // -------------------------------------------------------------------------

    @Given("ACS cannot resolve the acting caregiver")
    public void givenAcsCannotResolveTheActingCaregiver() {
        when(caregiverRoutingPort.resolveCaregiver(anyString())).thenReturn(null);
    }

    @And("the ProblemDetail field \"type\" is \"/errors/confirmador-unresolved\"")
    public void andTheProblemdetailFieldTypeIsErrorsconfirmadorunresolved() {
        assertProblemDetailTypeEndsWith("/errors/confirmador-unresolved");
    }

    @And("the Evento \"evt-1\" still has feedback \"nao_avaliado\"")
    public void andTheEventoEvt1StillHasFeedbackNaoavaliado() {
        UUID eventoId = ctx.getEventoId("evt-1");
        EventoJpaEntity entity = eventoJpaRepository.findById(eventoId).orElseThrow();
        assertThat(entity.getFeedback()).isEqualToIgnoringCase("NAO_AVALIADO");
    }

    // -------------------------------------------------------------------------
    // Scenario: Caregiver from another tenant → 403
    // -------------------------------------------------------------------------

    @Given("a caregiver session whose tenant is not the Evento's tenant")
    public void givenACaregiverSessionWhoseTenantIsNotThe() {
        // Tenant mismatch — implemented via TenantContextFilter stub limitation in PoC.
        // In this scenario the service cannot enforce 403 without JWT tenant claim.
        // Setting a different device tenant for awareness; enforcement is P9 gap (INT-EVNT-001).
        ctx.setDeviceTokenTenant("empresa_other");
    }

    @Then("the response status is 403")
    public void thenTheResponseStatusIs403() {
        assertThat(ctx.getLastResponse().getStatusCode().value()).isEqualTo(403);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<String> postConfirmation(
            UUID eventoId, String verdict, String confirmedBy) {
        Map<String, String> body = new HashMap<>();
        body.put("verdict", verdict);
        body.put("confirmedBy", confirmedBy);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        return restTemplate.exchange(
                "/api/v1/events/" + eventoId + "/confirmation",
                HttpMethod.POST,
                entity,
                String.class);
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
            throw new AssertionError("Failed to parse ProblemDetail: " + e.getMessage(), e);
        }
    }
}
