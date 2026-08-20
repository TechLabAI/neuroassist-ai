package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.NotificationDispatchBackPort;
import com.neuroassist.dash.infrastructure.clients.EvntClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter outbound: implementa {@link NotificationDispatchBackPort} delegando ao {@link EvntClient}.
 * Reporta ao EVNT o resultado da tentativa de entrega SSE de notificacoes ao cuidador.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchBackAdapter implements NotificationDispatchBackPort {

    private final EvntClient evntClient;

    @Override
    public void reportDelivered(String dispatchId) {
        log.debug("Reportando EVNT: notificacao [{}] entregue via SSE", dispatchId);
        evntClient.reportDelivered(dispatchId);
    }

    @Override
    public void reportAcknowledged(String dispatchId) {
        log.debug("Reportando EVNT: notificacao [{}] reconhecida pelo cuidador via SSE", dispatchId);
        evntClient.reportAcknowledgedCallback(dispatchId);
    }

    @Override
    public void reportNoSession(String dispatchId) {
        log.debug("Reportando EVNT: sem sessao SSE aberta para entrega da notificacao [{}]", dispatchId);
        evntClient.reportNoSession(dispatchId);
    }
}
