package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.application.port.in.HistAuditOutboxDrainUseCase.DrainResult;
import com.neuroassist.acs.application.port.out.HistAuditOutboxPort;
import com.neuroassist.acs.application.port.out.HistAuditOutboxPort.PendingFact;
import com.neuroassist.acs.application.port.out.HistAuditRedeliveryPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * E2E-031 — o dreno que faz o outbox do ACS significar alguma coisa.
 *
 * <p>O que se assegura aqui não é o caminho feliz, mas as três propriedades cuja ausência tornou o
 * outbox do pac-service inútil por um release inteiro (C-44): entradas saem da tabela, uma
 * indisponibilidade conhecida não queima o orçamento de retry de todas, e desistir é distinguível
 * de estar ocupado.
 *
 * <p>Mais uma que só existe aqui: <b>cada fato é entregue com o token da SUA empresa</b>. A tabela
 * é global e mistura tenants; entregar um fato com o token de outro o gravaria na trilha errada.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HistAuditOutboxDrainService — reentrega de fatos de autenticação")
class HistAuditOutboxDrainServiceTest {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 10;

    @Mock private HistAuditOutboxPort outboxPort;
    @Mock private HistAuditRedeliveryPort redeliveryPort;

    private HistAuditOutboxDrainService service;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new HistAuditOutboxDrainService(
                        outboxPort, redeliveryPort, BATCH_SIZE, MAX_RETRIES);
    }

    private PendingFact fact(UUID tenantId, int retries) {
        return new PendingFact(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "autenticacao.realizada",
                tenantId,
                "{\"factType\":\"autenticacao.realizada\"}",
                retries);
    }

    @Test
    @DisplayName("fato aceito sai da tabela como 'sent'")
    void deliveredFactIsMarkedSent() {
        PendingFact entry = fact(tenantA, 0);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(entry));
        when(redeliveryPort.redeliver(tenantA, entry.payloadJson())).thenReturn(true);

        DrainResult result = service.drain();

        verify(outboxPort).markSent(entry.id());
        assertThat(result).isEqualTo(new DrainResult(1, 1, 0));
    }

    @Test
    @DisplayName("cada fato é entregue com o tenant da SUA linha")
    void eachFactIsDeliveredWithItsOwnTenant() {
        PendingFact fromA = fact(tenantA, 0);
        PendingFact fromB = fact(tenantB, 0);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(fromA, fromB));
        when(redeliveryPort.redeliver(any(), anyString())).thenReturn(true);

        service.drain();

        // A tabela é global e mistura empresas. Entregar o fato de uma com o token de outra o
        // gravaria na trilha errada — vazamento entre empresas, não apenas um dado fora de lugar.
        verify(redeliveryPort).redeliver(tenantA, fromA.payloadJson());
        verify(redeliveryPort).redeliver(tenantB, fromB.payloadJson());
    }

    @Test
    @DisplayName("HIST fora do ar para o lote — não queima o retry dos demais")
    void outageStopsTheBatch() {
        PendingFact first = fact(tenantA, 0);
        PendingFact second = fact(tenantB, 0);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(first, second));
        when(redeliveryPort.redeliver(tenantA, first.payloadJson())).thenReturn(false);

        DrainResult result = service.drain();

        verify(outboxPort).markRetried(eq(first.id()), anyString());
        verify(redeliveryPort, never()).redeliver(tenantB, second.payloadJson());
        assertThat(result.delivered()).isZero();
    }

    @Test
    @DisplayName("esgotadas as tentativas, vira 'failed' e para de ser tentado")
    void exhaustedRetriesBecomeTerminal() {
        PendingFact exhausted = fact(tenantA, MAX_RETRIES);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(exhausted));

        DrainResult result = service.drain();

        verify(outboxPort).markFailed(eq(exhausted.id()), anyString());
        // Um estado terminal que continua chamando o HIST é indistinguível de fila ocupada —
        // foi exatamente assim que o C-44 passou despercebido.
        verifyNoInteractions(redeliveryPort);
        assertThat(result).isEqualTo(new DrainResult(1, 0, 1));
    }

    @Test
    @DisplayName("fila vazia não chama o HIST")
    void emptyQueueIsANoOp() {
        when(outboxPort.findPending(anyInt())).thenReturn(List.of());

        assertThat(service.drain()).isEqualTo(new DrainResult(0, 0, 0));
        verifyNoInteractions(redeliveryPort);
    }
}
