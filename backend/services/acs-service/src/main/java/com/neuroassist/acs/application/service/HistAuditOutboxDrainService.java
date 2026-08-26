package com.neuroassist.acs.application.service;

import com.neuroassist.acs.application.port.in.HistAuditOutboxDrainUseCase;
import com.neuroassist.acs.application.port.out.HistAuditOutboxPort;
import com.neuroassist.acs.application.port.out.HistAuditRedeliveryPort;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reentrega fatos de autenticação presos no outbox (E2E-031).
 *
 * <p>Terceira vez que este projeto escreve esta forma, e de propósito: uma linha durável que nada
 * lê de volta é pior do que linha nenhuma — a presença dela faz a fila parecer saudável enquanto
 * aquilo que ela representa nunca acontece (defeito C-44).
 *
 * <p><b>Sem iteração por tenant, ao contrário do PAC e do EVNT.</b> A tabela é global e cada linha
 * diz a que empresa pertence; não há {@code search_path} a vincular nem catálogo a percorrer. O
 * tenant da linha é usado para escopar o token de serviço da entrega — nunca um qualquer.
 *
 * <p><b>Lote limitado.</b> Um backlog acumulado numa indisponibilidade seria replayado numa única
 * transação, segurando conexão e martelando um hist-service que acabou de voltar.
 *
 * <p><b>Para no primeiro erro.</b> Se o hist-service está fora, as entradas seguintes falhariam
 * igual, e gastar o orçamento de retry delas contra uma indisponibilidade conhecida é como um
 * problema transitório vira permanente.
 */
@Slf4j
@Service
public class HistAuditOutboxDrainService implements HistAuditOutboxDrainUseCase {

    private final HistAuditOutboxPort outboxPort;
    private final HistAuditRedeliveryPort redeliveryPort;
    private final int batchSize;
    private final int maxRetries;

    public HistAuditOutboxDrainService(
            HistAuditOutboxPort outboxPort,
            HistAuditRedeliveryPort redeliveryPort,
            @Value("${acs.hist-outbox.batch-size:50}") int batchSize,
            @Value("${acs.hist-outbox.max-retries:10}") int maxRetries) {
        this.outboxPort = outboxPort;
        this.redeliveryPort = redeliveryPort;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    @Override
    @Transactional
    public DrainResult drain() {
        List<HistAuditOutboxPort.PendingFact> pending = outboxPort.findPending(batchSize);
        if (pending.isEmpty()) {
            return new DrainResult(0, 0, 0);
        }

        int delivered = 0;
        int givenUp = 0;
        for (HistAuditOutboxPort.PendingFact fact : pending) {
            if (fact.retryCount() >= maxRetries) {
                outboxPort.markFailed(fact.id(), "max retries exceeded");
                givenUp++;
                log.error(
                        "Audit fact {} ({}) desistido apos {} tentativas — tenant={}. NAO sera"
                                + " entregue; o hist-service nunca o aceitou.",
                        fact.factId(),
                        fact.factType(),
                        fact.retryCount(),
                        fact.tenantId());
                continue;
            }
            if (redeliveryPort.redeliver(fact.tenantId(), fact.payloadJson())) {
                outboxPort.markSent(fact.id());
                delivered++;
            } else {
                outboxPort.markRetried(fact.id(), "hist-service unavailable");
                break;
            }
        }

        log.info(
                "Audit outbox drain attempted={} delivered={} givenUp={}",
                pending.size(),
                delivered,
                givenUp);
        return new DrainResult(pending.size(), delivered, givenUp);
    }
}
