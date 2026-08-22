package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.NotificationOrchestrationUseCase;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.application.port.out.NotificationDispatchPort;
import com.neuroassist.evnt.application.port.out.NotificationDispatchRepositoryPort;
import com.neuroassist.evnt.domain.model.DispatchStatuses;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-attempts undelivered notification dispatches (defect C-45).
 *
 * <p>Two states are worth retrying, for different reasons:
 *
 * <ul>
 *   <li>{@code QUEUED} — dash-service could not be reached. Retrying is how the alarm survives an
 *       outage.
 *   <li>{@code NO_SESSION} — the caregiver had no panel open. Retrying is how the alarm reaches
 *       someone who stepped away, which is the ordinary case, not the exceptional one.
 * </ul>
 *
 * <p>{@code ACKNOWLEDGED} and {@code DELIVERED} are terminal and never re-sent.
 */
@Slf4j
@Service
public class NotificationOrchestrationService implements NotificationOrchestrationUseCase {

    private final NotificationDispatchRepositoryPort dispatchRepository;
    private final EventoRepositoryPort eventoRepository;
    private final NotificationDispatchPort dispatchPort;
    private final int batchSize;
    private final int maxAttempts;

    public NotificationOrchestrationService(
            NotificationDispatchRepositoryPort dispatchRepository,
            EventoRepositoryPort eventoRepository,
            NotificationDispatchPort dispatchPort,
            @Value("${evnt.dispatch-retry.batch-size:50}") int batchSize,
            @Value("${evnt.dispatch-retry.max-attempts:10}") int maxAttempts) {
        this.dispatchRepository = dispatchRepository;
        this.eventoRepository = eventoRepository;
        this.dispatchPort = dispatchPort;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Override
    @Transactional
    public SweepResult retrySweep() {
        String tenantId = TenantContextHolder.require(); // RULE-07

        List<NotificationDispatch> pending = dispatchRepository.findUndelivered(batchSize);
        if (pending.isEmpty()) {
            return new SweepResult(0, 0, 0, 0);
        }

        int delivered = 0;
        int stillPending = 0;
        int givenUp = 0;

        for (NotificationDispatch dispatch : pending) {
            if (dispatch.attempts() >= maxAttempts) {
                // Terminal, and loud: an alarm that will never be delivered must not look like a
                // queue that is merely busy.
                dispatchRepository.save(withStatus(dispatch, DispatchStatuses.FAILED));
                givenUp++;
                log.error(
                        "Dispatch {} to caregiver {} given up after {} attempts (tenant={}) — the"
                                + " alarm was never delivered",
                        dispatch.id(),
                        dispatch.caregiverId(),
                        dispatch.attempts(),
                        tenantId);
                continue;
            }

            String message =
                    eventoRepository
                            .findById(dispatch.eventoId())
                            .map(Evento::commandLabelSnapshot)
                            .orElse(null);

            NotificationDispatchPort.DispatchOutcome outcome;
            try {
                outcome = dispatchPort.dispatch(dispatch, message);
            } catch (Exception e) {
                log.warn("Dispatch {} retry threw: {}", dispatch.id(), e.getMessage());
                outcome =
                        new NotificationDispatchPort.DispatchOutcome(
                                NotificationDispatchPort.DispatchOutcome.FAILED);
            }

            String status = statusFor(outcome);
            dispatchRepository.save(
                    new NotificationDispatch(
                            dispatch.id(),
                            dispatch.eventoId(),
                            dispatch.tier(),
                            dispatch.caregiverId(),
                            status,
                            dispatch.attempts() + 1,
                            Instant.now()));

            if (DispatchStatuses.DELIVERED.equals(status)) {
                delivered++;
            } else {
                stillPending++;
            }
        }

        log.info(
                "Dispatch retry sweep tenant={} attempted={} delivered={} stillPending={}"
                        + " givenUp={}",
                tenantId,
                pending.size(),
                delivered,
                stillPending,
                givenUp);
        return new SweepResult(pending.size(), delivered, stillPending, givenUp);
    }

    private String statusFor(NotificationDispatchPort.DispatchOutcome outcome) {
        return switch (outcome.status()) {
            case NotificationDispatchPort.DispatchOutcome.DELIVERED -> DispatchStatuses.DELIVERED;
            case NotificationDispatchPort.DispatchOutcome.NO_OPEN_SESSION ->
                    DispatchStatuses.NO_SESSION;
            default -> DispatchStatuses.QUEUED;
        };
    }

    private NotificationDispatch withStatus(NotificationDispatch d, String status) {
        return new NotificationDispatch(
                d.id(),
                d.eventoId(),
                d.tier(),
                d.caregiverId(),
                status,
                d.attempts(),
                Instant.now());
    }
}
