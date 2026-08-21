package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.EventStatePort;
import com.neuroassist.dash.domain.model.NotificationPayload;
import com.neuroassist.dash.infrastructure.clients.EvntClient;
import com.neuroassist.dash.infrastructure.clients.dto.NotificationPayloadResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter outbound: implementa {@link EventStatePort} delegando ao {@link EvntClient}. Confirma
 * eventos e consulta/gerencia notificacoes nao reconhecidas via EVNT.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventStateAdapter implements EventStatePort {

    private final EvntClient evntClient;

    @Override
    public void confirmEvent(String eventId, String caregiverId) {
        log.debug(
                "Write-through EVNT: confirmacao de evento [{}] pelo cuidador [{}]",
                eventId,
                caregiverId);
        evntClient.confirmEvent(eventId, caregiverId);
    }

    @Override
    public List<NotificationPayload> getUnacknowledgedNotifications(String caregiverId) {
        log.debug(
                "Consultando EVNT: notificacoes nao reconhecidas para cuidador [{}]", caregiverId);
        try {
            return evntClient.getUnacknowledgedNotifications(caregiverId).stream()
                    .map(this::toNotificationPayload)
                    .toList();
        } catch (Exception ex) {
            // EVNT-read: soft-degrade — painel abre sem historico de notificacoes
            log.warn(
                    "EVNT indisponivel para consulta de notificacoes (soft-degrade) — cuidador [{}]: {}",
                    caregiverId,
                    ex.getMessage());
            return List.of();
        }
    }

    @Override
    public void acknowledgeNotification(String dispatchId, String caregiverId) {
        log.debug(
                "Write-through EVNT: ack da notificacao [{}] pelo cuidador [{}]",
                dispatchId,
                caregiverId);
        evntClient.acknowledgeNotification(dispatchId, caregiverId);
    }

    private NotificationPayload toNotificationPayload(NotificationPayloadResponse response) {
        return new NotificationPayload(
                response.dispatchId(),
                response.tier(),
                response.message(),
                Instant.parse(response.issuedAt()));
    }
}
