package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.EventConfirmationUseCase;
import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.application.port.out.SseEventBroadcastPort;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.domain.model.Feedback;
import com.neuroassist.evnt.shared.error.CodedException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventConfirmationService implements EventConfirmationUseCase {

    private static final String FACT_EVENTO_CONFIRMADO = "evento.confirmado";
    private static final String FACT_EVENTO_DESCARTADO = "evento.descartado";

    private final EventoRepositoryPort eventoRepositoryPort;
    private final CaregiverRoutingPort caregiverRoutingPort;
    private final AuditTrailPort auditTrailPort;
    private final SseEventBroadcastPort sseEventBroadcastPort;

    @Override
    public void confirm(UUID eventoId, String verdict, String confirmedBy, String requestTenant) {
        // 1. The confirmer must be a caregiver acs-service knows (EVNT-023)
        boolean known;
        try {
            known = caregiverRoutingPort.isKnownCaregiver(confirmedBy);
        } catch (Exception e) {
            // A causa era descartada aqui. O chamador recebia "confirmador nao resolvido" e o log
            // nao dizia se o ACS recusou, caiu, ou respondeu algo inesperado — tres problemas
            // diferentes com o mesmo texto. Perder a causa numa guarda de autorizacao e caro: e
            // exatamente onde alguem vai precisar saber POR QUE o acesso foi negado.
            log.error(
                    "Nao foi possivel resolver o confirmador {} contra o acs-service: {}: {}",
                    confirmedBy,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new CodedException(
                    "confirmador-unresolved", "Caregiver confirmador could not be resolved");
        }
        if (!known) {
            throw new CodedException(
                    "confirmador-unresolved", "Caregiver confirmador could not be resolved");
        }

        // 2. Load Evento
        Evento evento =
                eventoRepositoryPort
                        .findById(eventoId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Evento not found"));

        // 2a. Tenant isolation: request tenant must match evento tenant (EVNT-022)
        if (requestTenant != null
                && evento.tenantId() != null
                && !requestTenant.equals(evento.tenantId())) {
            throw new CodedException(
                    "tenant-mismatch", "Request tenant does not match evento tenant");
        }

        String existingFeedback = evento.feedback();

        // 3. Terminal-lock: different verdict on already-confirmed evento → 409 (EVNT-023)
        if (!Feedback.NAO_AVALIADO.name().equals(existingFeedback)) {
            String requestedFeedback =
                    "real".equalsIgnoreCase(verdict)
                            ? Feedback.REAL.name()
                            : Feedback.FALSO_POSITIVO.name();
            if (existingFeedback.equals(requestedFeedback)) {
                // Same verdict re-submitted — idempotent, return silently
                log.info(
                        "Idempotent confirmation eventoId={} feedback={}",
                        eventoId,
                        existingFeedback);
                return;
            }
            throw new CodedException("feedback-terminal", "Event feedback is terminal");
        }

        // 4. Apply verdict
        String feedback =
                "real".equalsIgnoreCase(verdict)
                        ? Feedback.REAL.name()
                        : Feedback.FALSO_POSITIVO.name();
        Instant confirmedAt = Instant.now();
        Evento updated =
                new Evento(
                        evento.id(),
                        evento.pacienteId(),
                        evento.comandoRef(),
                        evento.commandLabelSnapshot(),
                        evento.urgenciaSnapshot(),
                        feedback,
                        confirmedBy,
                        confirmedAt,
                        evento.recognizedAt(),
                        evento.createdAt(),
                        evento.seq(),
                        evento.tenantId());

        Evento saved = eventoRepositoryPort.save(updated);
        sseEventBroadcastPort.broadcast(saved);

        // 5. Audit trail — fail-open (AUD-07).
        // The caregiver is the actor of the fact even though the call itself is authenticated as
        // evnt-service or dash-service: authority and attribution are different things.
        String factType =
                Feedback.FALSO_POSITIVO.name().equals(feedback)
                        ? FACT_EVENTO_DESCARTADO
                        : FACT_EVENTO_CONFIRMADO;
        UUID subjectId = asUuid(evento.pacienteId());
        if (subjectId == null) {
            log.warn(
                    "Patient id {} is not a UUID — {} fact not recorded for evento {}",
                    evento.pacienteId(),
                    factType,
                    eventoId);
        } else {
            auditTrailPort.record(
                    new AuditTrailPort.AuditFact(
                            UUID.randomUUID(),
                            factType,
                            "paciente",
                            subjectId,
                            asUuid(confirmedBy),
                            confirmedBy,
                            Map.of(
                                    "eventoId", eventoId.toString(),
                                    "feedback", feedback,
                                    "comandoRef", String.valueOf(evento.comandoRef())),
                            confirmedAt));
        }
        log.info("Evento confirmed eventoId={} feedback={}", eventoId, feedback);
    }

    private UUID asUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
