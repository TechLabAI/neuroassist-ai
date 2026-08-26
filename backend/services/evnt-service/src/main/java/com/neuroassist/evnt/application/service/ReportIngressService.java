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
import com.neuroassist.evnt.domain.model.DispatchStatuses;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.domain.model.Feedback;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.domain.model.Tier;
import com.neuroassist.evnt.shared.error.CodedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReportIngressService implements ReportIngressUseCase {

    private static final String FACT_EVENTO_CRIADO = "evento.criado";

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

        // 0a. Tenant isolation: device token tenant must match patient tenant (EVNT-022).
        // deviceTenant is set by the controller from the device token's tenant_id claim, never
        // from the body — a caller must not be able to assert which tenant its device belongs to.
        if (request.deviceTenant() != null
                && request.patientTenant() != null
                && !request.deviceTenant().equals(request.patientTenant())) {
            throw new CodedException(
                    "tenant-mismatch", "Device token tenant does not match patient tenant");
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
            Evento existing = eventoRepositoryPort.findById(existingEventoId).orElse(null);
            return new ReportIngressResponse(
                    existingEventoId,
                    "duplicate",
                    existing == null
                            ? null
                            : new ReportIngressResponse.NotificationInfo(
                                    tierFor(existing.urgenciaSnapshot()), existing.confirmadoPor()),
                    Instant.now());
        }

        // 2. Validate the recognised primitives against gest-service — fail-closed
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

        // 3. Snapshot the command from pac-service — label and urgency are frozen here so a later
        // recalibration cannot rewrite what this alarm said
        CommandLookupPort.CommandSnapshot commandSnapshot =
                commandLookupPort.lookup(request.pacienteId(), request.commandRef());

        // 4. Resolve every caregiver responsible for the patient via the acs-service directory,
        // já na ordem determinística da escada (ADR-011 §1).
        List<CaregiverRoutingPort.ResponsibleCaregiver> caregivers =
                caregiverRoutingPort.responsibleFor(request.pacienteId());
        // Só os vínculos do paciente: o degrau 0 é sempre a cuidadora destinatária original, e
        // resolver a coordenadora da clínica aqui seria uma chamada a mais no caminho crítico de um
        // alarme para responder uma pergunta que só o escalonamento faz.
        List<CaregiverLadder.Rung> ladder = CaregiverLadder.build(caregivers, null);
        if (ladder.isEmpty()) {
            // Not an error the device can fix, but an alarm with no destination is a safety issue
            // that must be visible rather than logged and forgotten.
            log.error(
                    "No caregiver is responsible for patient {} — the alarm has no destination",
                    request.pacienteId());
        }

        // 5. Create Evento domain model (seq=null: DB fills bigserial on persist)
        Instant createdAt = Instant.now();
        Evento evento =
                new Evento(
                        UUID.randomUUID(),
                        request.pacienteId(),
                        request.commandRef(),
                        commandSnapshot.label(),
                        commandSnapshot.urgencia(),
                        Feedback.NAO_AVALIADO.name(),
                        null,
                        null,
                        request.recognizedAt(),
                        createdAt,
                        null,
                        request.patientTenant());

        // 6. Persist Evento (saved entity will carry DB-generated seq after fetch)
        Evento persisted = eventoRepositoryPort.save(evento);

        // 6a. Broadcast SSE frame to tenant's active connections
        sseEventBroadcastPort.broadcast(persisted);

        // 7. Register idempotency key
        reportIdempotencyRepositoryPort.save(request.reportId(), evento.id());

        // 8. Um dispatch para o degrau 0 da escada — a destinatária original (ADR-011 §1).
        //
        // Aqui já se criou um dispatch por cuidadora responsável, de uma vez. Era um leque, e o
        // ADR-011 decidiu que é uma escada: a cuidadora seguinte só é acionada porque a anterior
        // não recebeu. Notificar todas de imediato transformaria cada alarme de um paciente com
        // várias cuidadoras num alerta coletivo, e apagaria a informação que a auditoria precisa —
        // quem era a destinatária, e por que a próxima entrou.
        //
        // Os degraus seguintes nascem no NotificationOrchestrationService, quando a janela desta
        // cuidadora esgota. Nenhum alarme fica sem destinatária: a E2E-028 garante ao menos uma
        // cuidadora ativa por paciente.
        String tier = tierFor(commandSnapshot.urgencia());
        List<NotificationDispatch> dispatches = new ArrayList<>();
        if (!ladder.isEmpty()) {
            CaregiverLadder.Rung degrauZero = ladder.get(0);
            NotificationDispatch dispatch =
                    NotificationDispatch.queuedFor(
                            UUID.randomUUID(),
                            evento.id(),
                            tier,
                            degrauZero.cuidadorId(),
                            0,
                            degrauZero.grade(),
                            0,
                            null);
            dispatches.add(notificationDispatchRepositoryPort.save(dispatch));
        }

        // 9. Hand each dispatch to dash-service — AFTER this transaction commits.
        //
        // Doing it inline deadlocks the contract with dash-service: its delivery endpoint reports
        // the outcome back synchronously (POST /api/v1/notifications/{id}/delivered | /no-session),
        // and that callback runs in its own transaction, which cannot see a dispatch row this one
        // has not committed yet. The callback therefore answered 404, dash-service surfaced that as
        // an upstream error, and the dispatch stayed QUEUED forever — an alarm that looked sent and
        // was not.
        //
        // Fail-open is preserved: the Evento and the dispatch rows are already durable when the
        // delivery is attempted, so a dash-service outage leaves a retryable row rather than
        // rolling back the alarm.
        registerAfterCommit(() -> deliver(dispatches, commandSnapshot.label(), createdAt));
        // 10. Audit trail — fail-open (AUD-06)
        UUID subjectId = asUuid(request.pacienteId());
        UUID actorId = asUuid(request.deviceId());
        if (subjectId == null || actorId == null) {
            // hist-service types the subject as a UUID. The Evento itself is already persisted,
            // and audit is never allowed to fail the operation that produced it, so this is a
            // logged loss rather than an exception.
            log.warn(
                    "Patient id {} or device id {} is not a UUID — evento.criado fact not recorded"
                            + " for evento {}",
                    request.pacienteId(),
                    request.deviceId(),
                    evento.id());
        } else {
            auditTrailPort.record(
                    new AuditTrailPort.AuditFact(
                            UUID.randomUUID(),
                            FACT_EVENTO_CRIADO,
                            "paciente",
                            subjectId,
                            // hist-service types the actor as a caregiver and requires it
                            // (@NotNull on both the actor and its caregiverId). A gesture report
                            // has no human actor — the device produced it. Recording the device
                            // id here keeps the fact writable and attributable, but it does abuse
                            // a field named caregiverId: hist-service has no actor kind for a
                            // device, even though `dispositivo` is a valid *subject* kind. Raised
                            // as HIST backlog; until then the display name says what it really is.
                            actorId,
                            "dispositivo " + request.deviceId(),
                            Map.of(
                                    "eventoId", evento.id().toString(),
                                    "comandoRef", String.valueOf(request.commandRef()),
                                    "urgencia", String.valueOf(commandSnapshot.urgencia()),
                                    "tier", tier,
                                    "caregiverCount", ladder.size()),
                            createdAt));
        }

        log.info("Report ingested successfully eventoId={}", evento.id());
        return new ReportIngressResponse(
                evento.id(),
                "created",
                new ReportIngressResponse.NotificationInfo(
                        tier, ladder.isEmpty() ? null : ladder.get(0).cuidadorId()),
                createdAt);
    }

    /**
     * Runs {@code action} once the current transaction commits, or immediately when there is none
     * (the unit suites call the service outside a transaction).
     */
    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    private void deliver(
            List<NotificationDispatch> dispatches, String message, Instant attemptedAt) {
        for (NotificationDispatch dispatch : dispatches) {
            NotificationDispatchPort.DispatchOutcome outcome;
            try {
                outcome = notificationDispatchPort.dispatch(dispatch, message);
            } catch (Exception e) {
                // The adapter already absorbs its own failures, but the invariant belongs here:
                // the Evento is persisted before any notification is attempted, and no delivery
                // problem may roll that back.
                log.warn(
                        "Dispatch {} threw, staying queued for retry: {}",
                        dispatch.id(),
                        e.getMessage());
                outcome =
                        new NotificationDispatchPort.DispatchOutcome(
                                NotificationDispatchPort.DispatchOutcome.FAILED);
            }
            notificationDispatchRepositoryPort.save(
                    withDeliveryOutcome(dispatch, outcome, attemptedAt));
        }
    }

    private UUID asUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String tierFor(String urgencia) {
        return "emergencia".equalsIgnoreCase(urgencia)
                ? Tier.full_screen_alarm.name()
                : Tier.discreet.name();
    }

    private NotificationDispatch withDeliveryOutcome(
            NotificationDispatch dispatch,
            NotificationDispatchPort.DispatchOutcome outcome,
            Instant attemptedAt) {
        String status =
                switch (outcome.status()) {
                    case NotificationDispatchPort.DispatchOutcome.DELIVERED ->
                            DispatchStatuses.DELIVERED;
                    case NotificationDispatchPort.DispatchOutcome.NO_OPEN_SESSION ->
                            DispatchStatuses.NO_SESSION;
                        // FAILED, or anything dash-service invents later, stays retryable
                    default -> DispatchStatuses.QUEUED;
                };
        return dispatch.withAttempt(status, attemptedAt);
    }
}
