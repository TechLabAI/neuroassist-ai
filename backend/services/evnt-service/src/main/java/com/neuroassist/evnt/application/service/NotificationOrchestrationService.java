package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.NotificationOrchestrationUseCase;
import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.application.port.out.NotificationDispatchPort;
import com.neuroassist.evnt.application.port.out.NotificationDispatchRepositoryPort;
import com.neuroassist.evnt.domain.model.DispatchStatuses;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.domain.service.AlarmRelevancePolicy;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reentrega, escalonamento e expiração de alarmes — a implementação do ADR-011.
 *
 * <p><b>O que mudou e por quê.</b> O sweep antigo tinha um único desfecho para o alarme que não
 * chegava: ao atingir {@code max-attempts} a linha ia para {@code FAILED}, terminal e silencioso.
 * Ninguém era avisado, e a janela — cerca de cinco minutos — tinha sido escolhida por um contador
 * de retry, não por alguém. O ADR-011 substituiu isso por três regras, e as três valem juntas:
 *
 * <ol>
 *   <li><b>Escalonar.</b> Esgotada a janela de uma cuidadora, o alarme sobe para a próxima da
 *       escada. Escada, não leque: a seguinte só é acionada porque a anterior não recebeu.
 *   <li><b>Nunca desistir.</b> Esgotada a escada inteira, o alarme não morre — recomeça no degrau
 *       0, numa nova rodada, depois de um backoff.
 *   <li><b>Expirar por relevância clínica.</b> Só sai da fila o alarme cuja necessidade perdeu o
 *       sentido, segundo {@link AlarmRelevancePolicy} — e <b>um pedido de ajuda nunca sai</b>.
 * </ol>
 *
 * <p><b>Estados retentáveis</b> continuam sendo {@code QUEUED} (dash-service inalcançável) e {@code
 * NO_SESSION} (cuidadora sem painel aberto). {@code DELIVERED} e {@code ACKNOWLEDGED} são terminais
 * e nunca reenviados.
 */
@Slf4j
@Service
public class NotificationOrchestrationService implements NotificationOrchestrationUseCase {

    private final NotificationDispatchRepositoryPort dispatchRepository;
    private final EventoRepositoryPort eventoRepository;
    private final NotificationDispatchPort dispatchPort;
    private final CaregiverRoutingPort caregiverRoutingPort;
    private final AlarmRelevancePolicy relevancePolicy;
    private final int batchSize;
    private final int attemptsPerRung;
    private final Duration roundBackoff;

    public NotificationOrchestrationService(
            NotificationDispatchRepositoryPort dispatchRepository,
            EventoRepositoryPort eventoRepository,
            NotificationDispatchPort dispatchPort,
            CaregiverRoutingPort caregiverRoutingPort,
            AlarmRelevancePolicy relevancePolicy,
            @Value("${evnt.dispatch-retry.batch-size:50}") int batchSize,
            @Value("${evnt.dispatch-retry.max-attempts:10}") int attemptsPerRung,
            @Value("${evnt.escalation.round-backoff-ms:120000}") long roundBackoffMs) {
        this.dispatchRepository = dispatchRepository;
        this.eventoRepository = eventoRepository;
        this.dispatchPort = dispatchPort;
        this.caregiverRoutingPort = caregiverRoutingPort;
        this.relevancePolicy = relevancePolicy;
        this.batchSize = batchSize;
        this.attemptsPerRung = attemptsPerRung;
        this.roundBackoff = Duration.ofMillis(roundBackoffMs);
    }

    @Override
    @Transactional
    public SweepResult retrySweep() {
        String tenantId = TenantContextHolder.require(); // RULE-07

        Instant agora = Instant.now();
        List<NotificationDispatch> pending = dispatchRepository.findDueForRetry(agora, batchSize);
        if (pending.isEmpty()) {
            return new SweepResult(0, 0, 0, 0, 0);
        }

        int delivered = 0;
        int stillPending = 0;
        int escalated = 0;
        int expired = 0;

        for (NotificationDispatch dispatch : pending) {
            Evento evento = eventoRepository.findById(dispatch.eventoId()).orElse(null);

            // 1. Relevância clínica ANTES de qualquer outra coisa. É o único caminho de saída da
            //    fila que não passa por uma cuidadora reconhecendo o alarme — e ajuda nunca o toma.
            if (evento != null && expirouPorRelevancia(evento, agora)) {
                Duration idade = Duration.between(evento.createdAt(), agora);
                String motivo =
                        "expirou por relevancia clinica: urgencia=%s idade=%s comando=%s"
                                .formatted(
                                        evento.urgenciaSnapshot(),
                                        idade,
                                        String.valueOf(evento.comandoRef()));
                dispatchRepository.save(
                        dispatch.withOutcome(DispatchStatuses.FAILED, motivo, agora));
                expired++;
                log.warn(
                        "Dispatch {} (evento {}, cuidadora {}) saiu da fila — {} (tenant={})",
                        dispatch.id(),
                        dispatch.eventoId(),
                        dispatch.caregiverId(),
                        motivo,
                        tenantId);
                continue;
            }

            // 2. Janela desta cuidadora esgotada → sobe um degrau. Nunca vira FAILED por aqui.
            if (dispatch.attempts() >= attemptsPerRung) {
                escalated += escalate(dispatch, evento, agora, tenantId);
                continue;
            }

            // 3. Mais uma tentativa para a cuidadora deste degrau.
            String message = evento == null ? null : evento.commandLabelSnapshot();
            NotificationDispatchPort.DispatchOutcome outcome;
            try {
                outcome = dispatchPort.dispatch(dispatch, message);
            } catch (Exception e) {
                log.warn("Dispatch {} retry threw: {}", dispatch.id(), e.getMessage());
                outcome =
                        new NotificationDispatchPort.DispatchOutcome(
                                NotificationDispatchPort.DispatchOutcome.FAILED);
            }

            String status = statusFor(outcome);
            dispatchRepository.save(dispatch.withAttempt(status, agora));

            if (DispatchStatuses.DELIVERED.equals(status)) {
                delivered++;
            } else {
                stillPending++;
            }
        }

        log.info(
                "Dispatch retry sweep tenant={} attempted={} delivered={} stillPending={}"
                        + " escalated={} expired={}",
                tenantId,
                pending.size(),
                delivered,
                stillPending,
                escalated,
                expired);
        return new SweepResult(pending.size(), delivered, stillPending, escalated, expired);
    }

    private boolean expirouPorRelevancia(Evento evento, Instant agora) {
        return relevancePolicy.expirouPorRelevancia(
                evento.urgenciaSnapshot(),
                evento.comandoRef(),
                evento.commandLabelSnapshot(),
                evento.createdAt(),
                agora);
    }

    /**
     * Sobe o alarme um degrau da escada — ou reinicia a escada, se ela acabou.
     *
     * <p>Nos dois casos a linha atual é fechada com {@code ESCALATED} <b>e</b> o motivo por
     * escrito, e uma nova linha nasce viva na fila. O alarme não fica sem nenhuma linha viva em
     * momento algum: a linha nova é gravada na mesma transação que fecha a antiga.
     *
     * @return 1 quando o alarme efetivamente mudou de degrau ou de rodada, 0 quando não deu para
     *     escalonar agora (e a linha atual foi apenas adiada, nunca descartada)
     */
    private int escalate(
            NotificationDispatch dispatch, Evento evento, Instant agora, String tenantId) {
        if (evento == null) {
            // Sem o Evento não há paciente, e sem paciente não há escada. Adiar é a única resposta
            // honesta — descartar seria desistir de um alarme por um problema nosso.
            dispatchRepository.save(dispatch.deferredUntil(agora.plus(roundBackoff)));
            log.error(
                    "Dispatch {} sem Evento {} — escalonamento adiado, alarme mantido na fila"
                            + " (tenant={})",
                    dispatch.id(),
                    dispatch.eventoId(),
                    tenantId);
            return 0;
        }

        List<CaregiverLadder.Rung> ladder;
        try {
            // As duas fontes da escada. Cuidadoras e apoio vêm do vínculo com o PACIENTE; a
            // coordenadora é da CLÍNICA, e por isso é pergunta separada (ADR-011 §1).
            //
            // A coordenadora é resolvida fail-soft dentro do adapter: um grau que não pôde ser
            // resolvido faz o alarme seguir para o grau seguinte, nunca encerra a escada.
            ladder =
                    CaregiverLadder.build(
                            caregiverRoutingPort.responsibleFor(evento.pacienteId()),
                            caregiverRoutingPort.clinicCoordinators(
                                    // O Evento carrega o tenant do paciente; o do sweep é o mesmo,
                                    // e serve de rede quando a coluna é nula (Eventos anteriores à
                                    // E2E-022 não a preenchiam).
                                    evento.tenantId() == null ? tenantId : evento.tenantId()));
        } catch (Exception e) {
            // Fail-closed com memória: o directory fora do ar não inventa destinatária, mas também
            // não encerra o alarme. Ele volta ao sweep depois do backoff.
            dispatchRepository.save(dispatch.deferredUntil(agora.plus(roundBackoff)));
            log.error(
                    "Directory do ACS indisponivel ao escalonar dispatch {} (paciente {}): {} —"
                            + " alarme mantido na fila",
                    dispatch.id(),
                    evento.pacienteId(),
                    e.getMessage());
            return 0;
        }

        if (ladder.isEmpty()) {
            // A E2E-028 garante que todo paciente tem ao menos uma cuidadora ativa. Se a lista
            // chegou vazia, quem está errado é o directory — e o alarme fica na fila até que ele
            // volte a responder.
            dispatchRepository.save(dispatch.deferredUntil(agora.plus(roundBackoff)));
            log.error(
                    "Paciente {} sem nenhuma cuidadora no directory — alarme {} mantido na fila"
                            + " (tenant={})",
                    evento.pacienteId(),
                    dispatch.eventoId(),
                    tenantId);
            return 0;
        }

        int proximoDegrau = degrauAtual(dispatch, ladder) + 1;
        boolean escadaEsgotada = proximoDegrau >= ladder.size();

        int degrau = escadaEsgotada ? 0 : proximoDegrau;
        int rodada = escadaEsgotada ? dispatch.escalationRound() + 1 : dispatch.escalationRound();
        // O grau seguinte é acionado imediatamente; só a nova RODADA espera o backoff. Fazer o
        // seguinte esperar seria somar atraso a um alarme que já não chegou.
        Instant quando = escadaEsgotada ? agora.plus(roundBackoff) : null;
        CaregiverLadder.Rung proximo = ladder.get(degrau);

        String motivo =
                escadaEsgotada
                        ? ("escada esgotada na rodada %d apos %d tentativas para %s (%s);"
                                        + " reiniciando no degrau 0 (%s, grau %s) apos %s")
                                .formatted(
                                        dispatch.escalationRound(),
                                        dispatch.attempts(),
                                        dispatch.caregiverId(),
                                        grauDe(dispatch),
                                        proximo.cuidadorId(),
                                        proximo.grade(),
                                        roundBackoff)
                        : ("janela esgotada apos %d tentativas para %s (%s);"
                                        + " escalado para o degrau %d (%s, grau %s)")
                                .formatted(
                                        dispatch.attempts(),
                                        dispatch.caregiverId(),
                                        grauDe(dispatch),
                                        degrau,
                                        proximo.cuidadorId(),
                                        proximo.grade());

        dispatchRepository.save(dispatch.withOutcome(DispatchStatuses.ESCALATED, motivo, agora));
        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        UUID.randomUUID(),
                        dispatch.eventoId(),
                        dispatch.tier(),
                        proximo.cuidadorId(),
                        degrau,
                        proximo.grade(),
                        rodada,
                        quando));

        log.warn(
                "Alarme {} escalonado (tenant={}): {} — nova linha para {} no degrau {} (grau {})"
                        + " rodada {}",
                dispatch.eventoId(),
                tenantId,
                motivo,
                proximo.cuidadorId(),
                degrau,
                proximo.grade(),
                rodada);
        return 1;
    }

    /**
     * Onde esta linha está na escada de <b>agora</b>.
     *
     * <p>A composição da escada muda quando uma cuidadora é vinculada ou desvinculada do paciente,
     * e a posição gravada na linha pode não apontar mais para a mesma pessoa. Reconciliar pelo id
     * da destinatária é o que impede o escalonamento de pular alguém ou de repetir quem já falhou.
     * Se ela saiu da escada, {@code -1} faz o alarme retomar no degrau 0.
     */
    private int degrauAtual(NotificationDispatch dispatch, List<CaregiverLadder.Rung> ladder) {
        return CaregiverLadder.idsOf(ladder).indexOf(dispatch.caregiverId());
    }

    /** O grau gravado na linha, ou um marcador legível quando ela é anterior à V0502. */
    private String grauDe(NotificationDispatch dispatch) {
        return dispatch.escalationGrade() == null
                ? "grau nao registrado"
                : dispatch.escalationGrade().name();
    }

    private String statusFor(NotificationDispatchPort.DispatchOutcome outcome) {
        return switch (outcome.status()) {
            case NotificationDispatchPort.DispatchOutcome.DELIVERED -> DispatchStatuses.DELIVERED;
            case NotificationDispatchPort.DispatchOutcome.NO_OPEN_SESSION ->
                    DispatchStatuses.NO_SESSION;
            default -> DispatchStatuses.QUEUED;
        };
    }
}
