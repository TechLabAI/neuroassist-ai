package com.neuroassist.dash.application.service;

import com.neuroassist.dash.application.port.in.ConfirmationProxyUseCase;
import com.neuroassist.dash.application.port.out.EventStatePort;
import com.neuroassist.dash.application.port.out.NotificationDispatchBackPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementa write-throughs de confirmacao e ack para o EVNT.
 * DASH nao armazena resultado — puro proxy. ADR-006.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmationProxyService implements ConfirmationProxyUseCase {

    private final EventStatePort eventStatePort;
    private final NotificationDispatchBackPort notificationDispatchBackPort;

    /**
     * Confirma um evento no EVNT. Write-through: erro do EVNT e propagado (503).
     */
    @Override
    public void confirmEvent(String caregiverId, String eventId, String tenantId) {
        log.info("confirmEvent caregiverId={} eventId={} tenantId={}", caregiverId, eventId, tenantId);
        // Delega ao EVNT — hard dependency: erro propagado (503)
        eventStatePort.confirmEvent(eventId, caregiverId);
        log.info("confirmEvent ok eventId={}", eventId);
    }

    /**
     * Registra ack de notificacao no EVNT. Write-through: erro do EVNT e propagado (503).
     * Idempotencia gerenciada pelo EVNT (ADR-006: sem retry automatico no DASH).
     */
    @Override
    public void acknowledgeNotification(String caregiverId, String dispatchId, String tenantId) {
        log.info(
                "acknowledgeNotification caregiverId={} dispatchId={} tenantId={}",
                caregiverId, dispatchId, tenantId);
        // Reporta ack ao EVNT — hard dependency: erro propagado (503)
        notificationDispatchBackPort.reportAcknowledged(dispatchId);
        log.info("acknowledgeNotification ok dispatchId={}", dispatchId);
    }
}
