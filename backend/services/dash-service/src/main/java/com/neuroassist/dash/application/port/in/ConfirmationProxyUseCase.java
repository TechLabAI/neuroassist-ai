package com.neuroassist.dash.application.port.in;

/**
 * Inbound port — proxy de confirmacao de evento e ack de notificacao para o EVNT.
 */
public interface ConfirmationProxyUseCase {

    /** Confirma um evento no EVNT (write-through). Retorna void; HTTP 204. */
    void confirmEvent(String caregiverId, String eventId, String tenantId);

    /** Registra o ack de uma notificacao no EVNT (write-through). Retorna void; HTTP 204. */
    void acknowledgeNotification(String caregiverId, String dispatchId, String tenantId);
}
