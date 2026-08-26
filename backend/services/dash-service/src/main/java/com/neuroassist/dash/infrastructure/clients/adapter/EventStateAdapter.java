package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.EventStatePort;
import com.neuroassist.dash.domain.model.NotificationPayload;
import com.neuroassist.dash.infrastructure.clients.EvntClient;
import com.neuroassist.dash.infrastructure.clients.dto.NotificationPayloadResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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
    @CircuitBreaker(name = "evnt-service")
    public void confirmEvent(String eventId, String caregiverId) {
        log.debug(
                "Write-through EVNT: confirmacao de evento [{}] pelo cuidador [{}]",
                eventId,
                caregiverId);
        evntClient.confirmEvent(eventId, caregiverId);
    }

    /**
     * <b>Atenção ao que esta degradação significa</b> (E2E-018). Devolver lista vazia quando o EVNT
     * não responde faz "nenhuma notificação pendente" ficar indistinguível de "não consegui
     * perguntar" — e a diferença, para um alarme não reconhecido, é grande. A assinatura do port
     * não tem onde carregar essa distinção, e mudá-la é maior do que esta issue.
     *
     * <p>Duas coisas mitigam por ora: o log de falha subiu para {@code error}, e <b>nenhum serviço
     * de aplicação chama este método</b> — {@code grep} por ele no dash-service só encontra o
     * próprio client Feign. É scaffolding, não caminho vivo. Registrado assim para que a decisão
     * seja consciente quando alguém for usá-lo.
     */
    @Override
    @CircuitBreaker(name = "evnt-service", fallbackMethod = "notificationsUnavailable")
    public List<NotificationPayload> getUnacknowledgedNotifications(String caregiverId) {
        log.debug(
                "Consultando EVNT: notificacoes nao reconhecidas para cuidador [{}]", caregiverId);
        return evntClient.getUnacknowledgedNotifications(caregiverId).stream()
                .map(this::toNotificationPayload)
                .toList();
    }

    @SuppressWarnings("unused") // referenciado por nome em @CircuitBreaker
    private List<NotificationPayload> notificationsUnavailable(
            String caregiverId, Throwable cause) {
        log.error(
                "EVNT indisponivel para consulta de notificacoes — cuidador [{}] recebe lista"
                        + " VAZIA, indistinguivel de 'nada pendente': {}",
                caregiverId,
                cause.getMessage());
        return List.of();
    }

    /**
     * Sem {@code fallbackMethod} — e isso é a decisão, não um esquecimento.
     *
     * <p>Um fallback que devolvesse conjunto vazio faria "o EVNT não respondeu" ficar
     * indistinguível de "ninguém está pedindo ajuda". A falha sobe intacta; o breaker a contabiliza
     * (é por isso que ela não pode ser capturada aqui dentro) e o {@code PanelCompositionService}
     * traduz o silêncio em {@code hasActiveEvent = null}, que o painel sabe desenhar como "não sei"
     * em vez de "está tudo bem".
     */
    @Override
    @CircuitBreaker(name = "evnt-service")
    public Set<String> findPatientsWithActiveEvent(Collection<String> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return Set.of();
        }
        log.debug("Consultando EVNT: alarme ativo para {} paciente(s)", patientIds.size());
        EvntClient.ActiveEventsByPatientView view = evntClient.getActiveEventsByPatient(patientIds);
        return view.comAlarmeAtivo() == null ? Set.of() : Set.copyOf(view.comAlarmeAtivo());
    }

    @Override
    @CircuitBreaker(name = "evnt-service")
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
