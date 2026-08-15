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
import com.neuroassist.evnt.application.port.out.SseEventBroadcastPort;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.shared.error.CodedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SseEventBroadcastPort sseEventBroadcastPort;

    @Override
    public ReportIngressResponse ingest(ReportIngressRequest request) {
        log.info(
                "Ingesting report reportId={} pacienteId={}",
                request.reportId(),
                request.pacienteId());

        // 0a. Tenant isolation: device token tenant must match patient tenant (EVNT-022)
        if (request.deviceTenant() != null
                && request.patientTenant() != null
                && !request.deviceTenant().equals(request.patientTenant())) {
            throw new CodedException(
                    "tenant-mismatch",
                    "Device token tenant does not match patient tenant");
        }

        // 0b. Biometric payload guard (EVNT-023 — forbidden-biometric-payload)
        if (request.rawFramePayload() != null && !request.rawFramePayload().isBlank()) {
            throw new CodedException(
                    "forbidden-biometric-payload",
                    "Report must not include raw biometric frame payload");
        }

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
            throw new CodedException("gest-rejected", "Gesture report rejected by GEST service");
        }

        // 3. Lookup command snapshot (hardcoded for PoC)
        CommandLookupPort.CommandSnapshot commandSnapshot =
                commandLookupPort.lookup(request.commandRef());

        // 4. Resolve caregiver (hardcoded for PoC)
        String caregiverId = caregiverRoutingPort.resolveCaregiver(request.pacienteId());

        // 5. Create Evento domain model (seq=null: DB fills bigserial on persist)
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
                        createdAt,
                        null,
                        request.patientTenant());

        // 6. Create NotificationDispatch — tier derived from urgency (EVNT-022)
        String tier =
                "emergencia".equalsIgnoreCase(commandSnapshot.urgencia())
                        ? "full_screen_alarm"
                        : "discreet";
        NotificationDispatch dispatch =
                new NotificationDispatch(
                        UUID.randomUUID(), evento.id(), tier, caregiverId, "QUEUED", 0, null);

        // 7. Persist Evento (saved entity will carry DB-generated seq after fetch)
        Evento persisted = eventoRepositoryPort.save(evento);

        // 7a. Broadcast SSE frame to tenant's active connections
        sseEventBroadcastPort.broadcast(persisted);

        // 8. Persist NotificationDispatch
        notificationDispatchRepositoryPort.save(dispatch);

        // 9. Register idempotency key
        reportIdempotencyRepositoryPort.save(request.reportId(), evento.id());

        // 10. Dispatch notification — catch failures so the transaction commits (dispatch stays QUEUED)
        try {
            notificationDispatchPort.dispatch(dispatch);
        } catch (Exception e) {
            log.warn("Notification dispatch failed, dispatch stays QUEUED for retry: {}", e.getMessage());
        }

        // 11. Audit trail (no-op for PoC)
        auditTrailPort.append("EVENTO_CRIADO", evento.id().toString(), "PoC");

        // 12. Return response
        log.info("Report ingested successfully eventoId={}", evento.id());
        return new ReportIngressResponse(
                evento.id(),
                "created",
                new ReportIngressResponse.NotificationInfo(tier, caregiverId),
                createdAt);
    }
}
