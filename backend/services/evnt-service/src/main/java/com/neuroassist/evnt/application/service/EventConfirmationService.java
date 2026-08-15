package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.EventConfirmationUseCase;
import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.application.port.out.SseEventBroadcastPort;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.shared.error.CodedException;
import java.time.Instant;
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

    private final EventoRepositoryPort eventoRepositoryPort;
    private final CaregiverRoutingPort caregiverRoutingPort;
    private final AuditTrailPort auditTrailPort;
    private final SseEventBroadcastPort sseEventBroadcastPort;

    @Override
    public void confirm(UUID eventoId, String verdict, String confirmedBy, String requestTenant) {
        // 1. Resolve and validate confirmer via ACS (EVNT-023)
        String resolvedCaregiver;
        try {
            resolvedCaregiver =
                    caregiverRoutingPort.resolveCaregiver(confirmedBy != null ? confirmedBy : "");
        } catch (Exception e) {
            throw new CodedException(
                    "confirmador-unresolved", "Caregiver confirmador could not be resolved");
        }
        if (resolvedCaregiver == null || resolvedCaregiver.isBlank()) {
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
        if (!"NAO_AVALIADO".equals(existingFeedback)) {
            String requestedFeedback = "real".equalsIgnoreCase(verdict) ? "REAL" : "FALSO_POSITIVO";
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
        String feedback = "real".equalsIgnoreCase(verdict) ? "REAL" : "FALSO_POSITIVO";
        Evento updated =
                new Evento(
                        evento.id(),
                        evento.pacienteId(),
                        evento.comandoRef(),
                        evento.commandLabelSnapshot(),
                        evento.urgenciaSnapshot(),
                        feedback,
                        resolvedCaregiver,
                        Instant.now(),
                        evento.recognizedAt(),
                        evento.createdAt(),
                        evento.seq(),
                        evento.tenantId());

        Evento saved = eventoRepositoryPort.save(updated);
        sseEventBroadcastPort.broadcast(saved);

        // 5. Audit trail (EVNT-022)
        String auditAction =
                "FALSO_POSITIVO".equals(feedback) ? "FALSO_POSITIVO_MARCADO" : "EVENTO_CONFIRMADO";
        auditTrailPort.append(auditAction, eventoId.toString(), "confirmedBy=" + resolvedCaregiver);
        log.info("Evento confirmed eventoId={} feedback={}", eventoId, feedback);
    }
}
