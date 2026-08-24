package com.neuroassist.dash.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.neuroassist.dash.infrastructure.clients.dto.NotificationPayloadResponse;
import java.util.Collection;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client para o EVNT (Eventos e Alarmes). Confirma eventos, lista notificacoes nao
 * reconhecidas e reporta resultado de entrega SSE. RULE-06: FeignClient apenas em
 * infrastructure.clients.
 */
@FeignClient(
        name = "evnt-service",
        url = "${dash.evnt.base-url:}",
        contextId = "evntClient",
        configuration = EvntFeignConfig.class)
public interface EvntClient {

    /** Confirma um evento no EVNT (write-through do cuidador). */
    @PostMapping("/api/v1/events/{eventId}/confirm")
    void confirmEvent(
            @PathVariable("eventId") String eventId,
            @RequestParam("caregiverId") String caregiverId);

    /**
     * Quais destes pacientes têm pedido de ajuda em aberto — {@code GET
     * /api/v1/events/active/by-patient}.
     *
     * <p><b>Este endpoint precisou ser criado no EVNT.</b> O que existia era {@code GET
     * /api/v1/events/active}, que devolve <b>um</b> evento e cujo resumo não carrega {@code
     * pacienteId} — com ele, "este paciente tem alarme?" era literalmente irrespondível. Não era um
     * stub por ligar; era capacidade ausente.
     *
     * <p>A forma é de conjunto para não virar um N+1 sobre HTTP: o painel lista N pacientes.
     *
     * <p>Autorizado por {@code SVC_DASH} — a identidade de serviço que o {@link
     * EvntServiceIdentityInterceptor} já aplica a todas as chamadas deste cliente, e que o ACS já
     * emite para o cliente {@code dash-service}. Nenhuma authority nova.
     *
     * <p>Caminho, nome de parâmetro e forma da resposta copiados de {@code EventQueryController} e
     * de {@code EventQueryUseCase.ActiveEventsByPatientResponse} no produtor.
     */
    @GetMapping("/api/v1/events/active/by-patient")
    ActiveEventsByPatientView getActiveEventsByPatient(
            @RequestParam("pacienteIds") Collection<String> pacienteIds);

    /** Espelha {@code EventQueryUseCase.ActiveEventsByPatientResponse}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ActiveEventsByPatientView(List<String> consultados, List<String> comAlarmeAtivo) {}

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
