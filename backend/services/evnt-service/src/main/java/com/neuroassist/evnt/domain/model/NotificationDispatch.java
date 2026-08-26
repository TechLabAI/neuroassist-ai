package com.neuroassist.evnt.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma tentativa de levar um alarme a UMA cuidadora — um degrau da escada do ADR-011.
 *
 * <p>Zero imports de Spring/JPA (RULE-01, RULE-02).
 *
 * <p><b>Por que uma linha por degrau.</b> A pergunta que alguém faz depois de um incidente é "por
 * que a cuidadora X não foi avisada?". Ela só tem resposta se cada degrau tentado deixar a sua
 * própria linha, com o instante da última tentativa e o desfecho declarado. Reaproveitar a mesma
 * linha, trocando a destinatária, apagaria exatamente o histórico que interessa.
 *
 * @param escalationOrder degrau na escada de cuidadoras — 0 é a destinatária original
 * @param escalationGrade por que esta pessoa entrou na escada — o grau normativo do ADR-011. O
 *     índice diz "foi a terceira da fila"; o grau diz "porque nenhuma cuidadora do paciente
 *     recebeu". Sem ele não há como responder "por que a coordenadora foi acionada?"
 * @param escalationRound quantas passadas completas da escada já ocorreram para este alarme; sobe
 *     de 1 toda vez que a escada se esgota e recomeça (ADR-011 §2 — nunca desistir)
 * @param nextAttemptAt instante a partir do qual o sweep pode tentar de novo; {@code null} =
 *     elegível já no próximo tick. É o backoff entre rodadas
 * @param outcomeReason por que esta linha saiu da fila, em texto legível. Preenchido quando o sweep
 *     declara um desfecho ({@code ESCALATED} ou {@code FAILED}); {@code null} enquanto a linha
 *     ainda está viva
 */
public record NotificationDispatch(
        UUID id,
        UUID eventoId,
        String tier,
        String caregiverId,
        String status,
        int attempts,
        Instant lastAttemptAt,
        int escalationOrder,
        EscalationGrade escalationGrade,
        int escalationRound,
        Instant nextAttemptAt,
        String outcomeReason) {

    /** Novo degrau da escada, ainda sem tentativa alguma. */
    public static NotificationDispatch queuedFor(
            UUID id,
            UUID eventoId,
            String tier,
            String caregiverId,
            int escalationOrder,
            EscalationGrade escalationGrade,
            int escalationRound,
            Instant nextAttemptAt) {
        return new NotificationDispatch(
                id,
                eventoId,
                tier,
                caregiverId,
                DispatchStatuses.QUEUED,
                0,
                null,
                escalationOrder,
                escalationGrade,
                escalationRound,
                nextAttemptAt,
                null);
    }

    /**
     * Resultado de mais uma tentativa: conta a tentativa, registra quando ela ocorreu e limpa
     * qualquer desfecho anterior — uma linha que voltou a ser tentada não saiu da fila.
     */
    public NotificationDispatch withAttempt(String newStatus, Instant attemptedAt) {
        return new NotificationDispatch(
                id,
                eventoId,
                tier,
                caregiverId,
                newStatus,
                attempts + 1,
                attemptedAt,
                escalationOrder,
                escalationGrade,
                escalationRound,
                null,
                null);
    }

    /** Mudança de status sem consumir uma tentativa (callbacks do dash-service, por exemplo). */
    public NotificationDispatch withStatus(String newStatus, Instant at) {
        return new NotificationDispatch(
                id,
                eventoId,
                tier,
                caregiverId,
                newStatus,
                attempts,
                at,
                escalationOrder,
                escalationGrade,
                escalationRound,
                nextAttemptAt,
                null);
    }

    /**
     * Saída declarada da fila: status terminal <b>e</b> o motivo por escrito. As duas coisas
     * juntas, de propósito — o ADR-011 exige que nenhum alarme saia da fila sem deixar registro de
     * que saiu e por quê.
     */
    public NotificationDispatch withOutcome(String terminalStatus, String reason, Instant at) {
        return new NotificationDispatch(
                id,
                eventoId,
                tier,
                caregiverId,
                terminalStatus,
                attempts,
                at,
                escalationOrder,
                escalationGrade,
                escalationRound,
                null,
                reason);
    }

    /** Adia a próxima tentativa sem consumir tentativa nem alterar o status. */
    public NotificationDispatch deferredUntil(Instant when) {
        return new NotificationDispatch(
                id,
                eventoId,
                tier,
                caregiverId,
                status,
                attempts,
                lastAttemptAt,
                escalationOrder,
                escalationGrade,
                escalationRound,
                when,
                outcomeReason);
    }
}
