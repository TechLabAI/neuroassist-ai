package com.neuroassist.acs.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port — a metade durável de {@link HistAuditPort} (E2E-031).
 *
 * <p>Fail-open sem durabilidade é o pior dos dois mundos: o chamador não fica sabendo, e o fato
 * some. Este port é o que torna a perda impossível em vez de meramente visível.
 *
 * <p><b>Cada entrada carrega o seu tenant.</b> Ao contrário dos outbox do PAC e do EVNT, que vivem
 * no schema do tenant e o herdam dali, este é global — o acs-service grava fatos de várias
 * empresas. A entrega precisa escopar o token de serviço ao tenant <i>daquela linha</i>.
 */
public interface HistAuditOutboxPort {

    /**
     * Guarda um fato para entrega posterior.
     *
     * <p>Idempotente em {@code factId}: enfileirar um fato que já está na tabela é no-op. A
     * tentativa direta e esta gravação são dois caminhos para um mesmo fato, e ele nunca pode
     * entrar na fila duas vezes — o hist-service deduplicaria, o que esconderia o defeito em vez de
     * o denunciar.
     */
    void enqueue(UUID factId, String factType, UUID tenantId, String payloadJson);

    /**
     * Mais antigas primeiro, limitado, para que uma rajada não afame o que espera há mais tempo.
     */
    List<PendingFact> findPending(int limit);

    void markSent(UUID id);

    /** Registra uma tentativa falha; a entrada continua {@code pending}. */
    void markRetried(UUID id, String error);

    /**
     * Desiste após tentativas demais, com o motivo que a encerrou.
     *
     * <p>Terminal de propósito: uma entrada retentada para sempre é indistinguível de fila
     * saudável, e esconde um fato que nunca será entregue atrás de um número que só cresce.
     */
    void markFailed(UUID id, String error);

    record PendingFact(
            UUID id,
            UUID factId,
            String factType,
            UUID tenantId,
            String payloadJson,
            int retryCount) {}
}
