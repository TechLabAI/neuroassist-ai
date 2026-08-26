package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.NotificationCallbackUseCase;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.application.port.out.NotificationDispatchRepositoryPort;
import com.neuroassist.evnt.domain.model.DispatchStatuses;
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
public class NotificationCallbackService implements NotificationCallbackUseCase {

    private final NotificationDispatchRepositoryPort dispatchRepository;
    private final EventoRepositoryPort eventoRepository;

    @Override
    public void markDelivered(UUID dispatchId) {
        transition(dispatchId, DispatchStatuses.DELIVERED);
    }

    @Override
    public void markNoSession(UUID dispatchId) {
        transition(dispatchId, DispatchStatuses.NO_SESSION);
    }

    @Override
    public void markAcknowledged(UUID dispatchId, String caregiverId) {
        NotificationDispatch dispatch = require(dispatchId);
        if (DispatchStatuses.ACKNOWLEDGED.equals(dispatch.status())) {
            // dash-service reports the ack from both its panel proxy and its SSE callback, so the
            // second arrival is expected rather than exceptional.
            log.debug("Dispatch {} already acknowledged", dispatchId);
            return;
        }
        if (caregiverId != null
                && dispatch.caregiverId() != null
                && !caregiverId.equals(dispatch.caregiverId())) {
            log.warn(
                    "Dispatch {} acknowledged by {} but was addressed to {}",
                    dispatchId,
                    caregiverId,
                    dispatch.caregiverId());
        }
        dispatchRepository.save(withStatus(dispatch, DispatchStatuses.ACKNOWLEDGED));
        log.info("Dispatch {} acknowledged by {}", dispatchId, caregiverId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingNotification> listUnacknowledged(String caregiverId) {
        return dispatchRepository.findUnacknowledgedFor(caregiverId).stream()
                .map(this::toPending)
                .toList();
    }

    private PendingNotification toPending(NotificationDispatch dispatch) {
        Evento evento = eventoRepository.findById(dispatch.eventoId()).orElse(null);
        String message = evento == null ? null : evento.commandLabelSnapshot();
        String issuedAt =
                evento == null || evento.createdAt() == null ? null : evento.createdAt().toString();
        return new PendingNotification(
                dispatch.id().toString(), dispatch.tier(), message, issuedAt);
    }

    private void transition(UUID dispatchId, String status) {
        NotificationDispatch dispatch = require(dispatchId);
        if (DispatchStatuses.ACKNOWLEDGED.equals(dispatch.status())) {
            // An ack outranks a delivery report: dash-service may report delivery after the
            // caregiver has already acknowledged, and that must not undo the terminal state.
            log.debug("Dispatch {} is acknowledged; ignoring {} callback", dispatchId, status);
            return;
        }
        dispatchRepository.save(withStatus(dispatch, status));
        log.info("Dispatch {} → {}", dispatchId, status);
    }

    private NotificationDispatch require(UUID dispatchId) {
        return dispatchRepository
                .findById(dispatchId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Dispatch not found"));
    }

    private NotificationDispatch withStatus(NotificationDispatch dispatch, String status) {
        return dispatch.withStatus(status, Instant.now());
    }
}
