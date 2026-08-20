package com.neuroassist.dash.infrastructure.clients;

import com.neuroassist.dash.infrastructure.clients.dto.NotificationPayloadResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client para o EVNT (Eventos e Alarmes).
 * Confirma eventos, lista notificacoes nao reconhecidas e reporta resultado de entrega SSE.
 * RULE-06: FeignClient apenas em infrastructure.clients.
 */
@FeignClient(name = "evnt-service", url = "${dash.evnt.base-url:}", contextId = "evntClient")
public interface EvntClient {

    /** Confirma um evento no EVNT (write-through do cuidador). */
    @PostMapping("/api/v1/events/{eventId}/confirm")
    void confirmEvent(
            @PathVariable("eventId") String eventId,
            @RequestParam("caregiverId") String caregiverId);

    /** Lista notificacoes nao reconhecidas para o cuidador. */
    @GetMapping("/api/v1/notifications/unacknowledged")
    List<NotificationPayloadResponse> getUnacknowledgedNotifications(
            @RequestParam("caregiverId") String caregiverId);

    /** Registra o reconhecimento (ack) de uma notificacao pelo cuidador. */
    @PostMapping("/api/v1/notifications/{dispatchId}/ack")
    void acknowledgeNotification(
            @PathVariable("dispatchId") String dispatchId,
            @RequestParam("caregiverId") String caregiverId);

    /** Informa ao EVNT que a notificacao foi entregue com sucesso via SSE. */
    @PostMapping("/api/v1/notifications/{dispatchId}/delivered")
    void reportDelivered(@PathVariable("dispatchId") String dispatchId);

    /** Informa ao EVNT que nao havia sessao SSE aberta para a entrega. */
    @PostMapping("/api/v1/notifications/{dispatchId}/no-session")
    void reportNoSession(@PathVariable("dispatchId") String dispatchId);

    /** Callback interno: informa ao EVNT que o cuidador reconheceu (ack) via SSE. */
    @PostMapping("/api/v1/notifications/{dispatchId}/ack")
    void reportAcknowledgedCallback(@PathVariable("dispatchId") String dispatchId);
}
