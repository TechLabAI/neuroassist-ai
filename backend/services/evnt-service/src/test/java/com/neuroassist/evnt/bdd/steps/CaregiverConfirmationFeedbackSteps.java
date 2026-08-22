package com.neuroassist.evnt.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.bdd.BddIds;
import com.neuroassist.evnt.bdd.ScenarioContext;
import com.neuroassist.evnt.bdd.TestJwtDecoderConfig;
import com.neuroassist.evnt.infrastructure.persistence.entity.EventoJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.EventoJpaRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — Caregiver confirmation and false-positive feedback (P8: EVNT-023). Feature:
 * caregiver-confirmation-feedback.feature
 *
 * <p>NOTE: "the caregiver posts verdict {string} to {string}" uses {string} parameters instead of
 * literal "/api/v1/..." paths to avoid Cucumber treating "/" as alternation.
 */
@RequiredArgsConstructor
public class CaregiverConfirmationFeedbackSteps {

    private final ScenarioContext ctx;

    private final TestRestTemplate restTemplate;

    private final EventoJpaRepository eventoJpaRepository;

    private final CaregiverRoutingPort caregiverRoutingPort;

    private final AuditTrailPort auditTrailPort;

    /** The UUID for the pre-created Evento "evt-1". Stored in ScenarioContext as alias. */
    private UUID evt1Id;

    // -------------------------------------------------------------------------
    // Background steps
    // -------------------------------------------------------------------------

    @Given("a tenant \"empresa_demo\" with an Evento \"evt-1\" in feedback state \"nao_avaliado\"")
    public void givenATenantEmpresademoWithAnEventoEvt1In() {
        ctx.setTenantId("empresa_demo");
        evt1Id = UUID.randomUUID();
        EventoJpaEntity entity =
                EventoJpaEntity.builder()
                        .id(evt1Id)
                        .pacienteId(BddIds.PACIENTE)
                        .comandoRef("cmd-emergency")
                        .commandLabelSnapshot("Emergency Command")
                        .urgenciaSnapshot("emergencia")
                        .feedback("NAO_AVALIADO")
                        .recognizedAt(Instant.now())
                        .createdAt(Instant.now())
                        .tenantId("empresa_demo")
                        .build();
        eventoJpaRepository.save(entity);
        ctx.registerEvento("evt-1", evt1Id);
    }

    @And("a caregiver session for \"cg-1\" authorized for the Evento's patient")
    public void andACaregiverSessionForCg1AuthorizedForThe() {
        when(caregiverRoutingPort.responsibleFor(anyString()))
                .thenReturn(
                        java.util.List.of(
                                new CaregiverRoutingPort.ResponsibleCaregiver(
                                        BddIds.CUIDADOR, "CUIDADORA")));
        when(caregiverRoutingPort.isKnownCaregiver(anyString())).thenReturn(true);
    }

    @And("ACS resolves \"cg-1\" as a valid Cuidador")
    public void andAcsResolvesCg1AsAValidCuidador() {
        when(caregiverRoutingPort.responsibleFor(anyString()))
                .thenReturn(
                        java.util.List.of(
                                new CaregiverRoutingPort.ResponsibleCaregiver(
                                        BddIds.CUIDADOR, "CUIDADORA")));
        when(caregiverRoutingPort.isKnownCaregiver(anyString())).thenReturn(true);
    }

    // -------------------------------------------------------------------------
    // When — generic verdict step (avoids literal "/" in annotation causing alternation)
    // -------------------------------------------------------------------------

    @When("the caregiver posts verdict {string} to {string}")
    public void whenTheCaregiverPostsVerdictTo(String verdict, String path) {
        UUID eventoId = ctx.getEventoId("evt-1");
        ResponseEntity<String> response = postConfirmation(eventoId, verdict, BddIds.CUIDADOR);
        ctx.setLastResponse(response);
    }

    // -------------------------------------------------------------------------
    // Then — feedback assertions
    // -------------------------------------------------------------------------

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
                .isEqualTo(BddIds.CUIDADOR);
        assertThat(entity.getConfirmadoAt()).as("confirmadoAt should be set").isNotNull();
    }

    @And("an audit fact \"EventoConfirmado\" was appended to HIST")
    public void andAnAuditFactEventoconfirmadoWasAppendedToHist() {
        verifyFactRecorded("evento.confirmado", ctx.getEventoId("evt-1").toString());
    }

    @And("the Evento \"evt-1\" has feedback \"falso_positivo\"")
    public void andTheEventoEvt1HasFeedbackFalsopositivo() {
        UUID eventoId = ctx.getEventoId("evt-1");
        EventoJpaEntity entity = eventoJpaRepository.findById(eventoId).orElseThrow();
        assertThat(entity.getFeedback()).isEqualToIgnoringCase("FALSO_POSITIVO");
    }

    @And("an audit fact \"FalsoPositivoMarcado\" was appended to HIST")
    public void andAnAuditFactFalsopositivomarcadoWasAppendedToHist() {
        verifyFactRecorded("evento.descartado", ctx.getEventoId("evt-1").toString());
    }

    @And("the false-positive rate for the Evento's command reflects the new verdict")
    public void andTheFalsepositiveRateForTheEventosCommandReflects() {
        verifyFactRecorded("evento.descartado", null);
    }

    // -------------------------------------------------------------------------
    // Scenario: Feedback is terminal - conflicting second verdict → 409
    // -------------------------------------------------------------------------

    @Given("the Evento \"evt-1\" already has feedback \"real\"")
    public void givenTheEventoEvt1AlreadyHasFeedbackReal() {
        UUID eventoId = ctx.getEventoId("evt-1");
        EventoJpaEntity entity = eventoJpaRepository.findById(eventoId).orElseThrow();
        entity.setFeedback("REAL");
        entity.setConfirmadoPor(BddIds.CUIDADOR);
        entity.setConfirmadoAt(Instant.now());
        eventoJpaRepository.save(entity);
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
        when(caregiverRoutingPort.responsibleFor(anyString())).thenReturn(java.util.List.of());
        when(caregiverRoutingPort.isKnownCaregiver(anyString())).thenReturn(false);
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
        ctx.setDeviceTokenTenant("empresa_other");
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
        headers.set("X-Tenant-Id", ctx.getDeviceTokenTenant());
        headers.setBearerAuth(
                TestJwtDecoderConfig.token(
                        ctx.getDeviceTokenTenant(),
                        confirmedBy,
                        "evnt.event-confirmation.confirmar"));
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        return restTemplate.exchange(
                "/api/v1/events/" + eventoId + "/confirmation",
                HttpMethod.POST,
                entity,
                String.class);
    }

    /**
     * Asserts a fact of {@code factType} reached the audit port, optionally naming the evento it
     * must be about.
     *
     * <p>The fact type is now the hist-service vocabulary value ({@code evento.confirmado} / {@code
     * evento.descartado}) rather than the free-form {@code EVENTO_CONFIRMADO} string the no-op
     * adapter used to swallow — hist-service rejects anything outside {@code
     * hist.fact-types.allowed} with 422.
     */
    private void verifyFactRecorded(String factType, String eventoId) {
        ArgumentCaptor<AuditTrailPort.AuditFact> captor =
                ArgumentCaptor.forClass(AuditTrailPort.AuditFact.class);
        verify(auditTrailPort, atLeastOnce()).record(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(
                        fact -> {
                            assertThat(fact.factType()).isEqualTo(factType);
                            if (eventoId != null) {
                                assertThat(fact.payload()).containsEntry("eventoId", eventoId);
                            }
                        });
    }
}
