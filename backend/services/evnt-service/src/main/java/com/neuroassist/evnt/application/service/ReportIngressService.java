package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.ReportIngressUseCase;
import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.application.port.out.CommandLookupPort;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.application.port.out.GestValidationPort;
import com.neuroassist.evnt.application.port.out.NotificationDispatchPort;
import com.neuroassist.evnt.application.port.out.NotificationDispatchRepositoryPort;
import com.neuroassist.evnt.application.port.out.ReportIdempotencyRepositoryPort;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import java.time.Instant;
import java.util.List;
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
public class ReportIngressService implements ReportIngressUseCase {

    private final GestValidationPort gestValidationPort;
    private final CommandLookupPort commandLookupPort;
    private final CaregiverRoutingPort caregiverRoutingPort;
    private final NotificationDispatchPort notificationDispatchPort;
    private final AuditTrailPort auditTrailPort;
    private final EventoRepositoryPort eventoRepositoryPort;
    private final NotificationDispatchRepositoryPort notificationDispatchRepositoryPort;
    private final ReportIdempotencyRepositoryPort reportIdempotencyRepositoryPort;

    @Override
    public ReportIngressResponse ingest(ReportIngressRequest request) {
        log.info(
                "Ingesting report reportId={} pacienteId={}",
                request.reportId(),
                request.pacienteId());

        // 1. Idempotency check
        if (reportIdempotencyRepositoryPort.exists(request.reportId())) {
            UUID existingEventoId =
                    reportIdempotencyRepositoryPort.findEventoId(request.reportId()).orElseThrow();
            log.info(
                    "Duplicate report detected reportId={} eventoId={}",
                    request.reportId(),
                    existingEventoId);
            return new ReportIngressResponse(
                    existingEventoId,
                    "duplicate",
                    new ReportIngressResponse.NotificationInfo(
                            "FULL_SCREEN_ALARM", "cuidadora-jamilly-fixo"),
                    Instant.now());
        }

        // 2. Validate gesture with gest-service
        List<String> tipoGestoIds =
                request.primitives() != null
                        ? request.primitives().stream()
                                .map(ReportIngressRequest.PrimitiveEntry::tipoGestoId)
                                .toList()
                        : List.of();
        boolean valid =
                gestValidationPort.validate(
                        new GestValidationPort.GestValidationRequest(
                                request.profileVersion(), tipoGestoIds));
        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Gesture report validation failed");
        }

        // 3. Lookup command snapshot (hardcoded for PoC)
        CommandLookupPort.CommandSnapshot commandSnapshot =
                commandLookupPort.lookup(request.commandRef());

        // 4. Resolve caregiver (hardcoded for PoC)
        String caregiverId = caregiverRoutingPort.resolveCaregiver(request.pacienteId());

        // 5. Create Evento domain model
        Instant createdAt = Instant.now();
        Evento evento =
                new Evento(
                        UUID.randomUUID(),
                        request.pacienteId(),
                        request.commandRef(),
                        commandSnapshot.label(),
                        commandSnapshot.urgencia(),
                        "NAO_AVALIADO",
                        null,
                        null,
                        request.recognizedAt(),
                        createdAt);

        // 6. Create NotificationDispatch
        NotificationDispatch dispatch =
                new NotificationDispatch(
                        UUID.randomUUID(),
                        evento.id(),
                        "FULL_SCREEN_ALARM",
                        caregiverId,
                        "QUEUED",
                        0,
                        null);

        // 7. Persist Evento
        eventoRepositoryPort.save(evento);

        // 8. Persist NotificationDispatch
        notificationDispatchRepositoryPort.save(dispatch);

        // 9. Register idempotency key
        reportIdempotencyRepositoryPort.save(request.reportId(), evento.id());

        // 10. Dispatch notification (log for PoC)
        notificationDispatchPort.dispatch(dispatch);

        // 11. Audit trail (no-op for PoC)
        auditTrailPort.append("EVENTO_CRIADO", evento.id().toString(), "PoC");

        // 12. Return response
        log.info("Report ingested successfully eventoId={}", evento.id());
        return new ReportIngressResponse(
                evento.id(),
                "created",
                new ReportIngressResponse.NotificationInfo("FULL_SCREEN_ALARM", caregiverId),
                createdAt);
    }
}
