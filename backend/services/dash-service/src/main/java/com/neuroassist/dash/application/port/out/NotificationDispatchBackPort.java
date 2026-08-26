package com.neuroassist.dash.application.port.out;

/**
 * Outbound port — callbacks de entrega de notificacao para o EVNT. Permite que o DASH reporte o
 * resultado da tentativa de entrega SSE ao cuidador.
 */
public interface NotificationDispatchBackPort {

    /** Informa ao EVNT que a notificacao foi entregue com sucesso ao cuidador. */
    void reportDelivered(String dispatchId);

    /** Informa ao EVNT que o cuidador reconheceu (ack) a notificacao. */
    void reportAcknowledged(String dispatchId);

    /** Informa ao EVNT que nao havia sessao SSE aberta para entregar a notificacao. */
    void reportNoSession(String dispatchId);
}
