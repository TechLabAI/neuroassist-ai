package com.neuroassist.dash.application.port.out;

import com.neuroassist.dash.domain.model.NotificationPayload;
import java.util.List;

/** Outbound port — interacao com o EVNT para eventos e notificacoes. */
public interface EventStatePort {

    /** Confirma um evento no EVNT (write-through). */
    void confirmEvent(String eventId, String caregiverId);

    /** Retorna notificacoes nao reconhecidas para o cuidador. */
    List<NotificationPayload> getUnacknowledgedNotifications(String caregiverId);

    /** Registra o reconhecimento (ack) de uma notificacao no EVNT. */
    void acknowledgeNotification(String dispatchId, String caregiverId);
}
