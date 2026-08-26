package com.neuroassist.evnt.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neuroassist.evnt.application.port.in.HistOutboxDrainUseCase.DrainResult;
import com.neuroassist.evnt.application.port.out.HistFactRedeliveryPort;
import com.neuroassist.evnt.application.port.out.HistOutboxPort;
import com.neuroassist.evnt.application.port.out.HistOutboxPort.PendingFact;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * E2E-017 — the drain that makes evnt-service's audit outbox mean something.
 *
 * <p>What is asserted here is not the happy path but the three properties whose absence made the
 * equivalent pac-service code useless for a whole release (defect C-44): entries leave the table, a
 * known outage does not burn every entry's retry budget, and giving up is distinguishable from
 * being busy.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HistOutboxDrainService — reentrega de fatos de auditoria do EVNT")
class HistOutboxDrainServiceTest {

    private static final String TENANT = "e2e00000-0000-0000-0000-000000000001";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 10;

    @Mock private HistOutboxPort outboxPort;
    @Mock private HistFactRedeliveryPort redeliveryPort;

    private HistOutboxDrainService service;

    @BeforeEach
    void setUp() {
        service = new HistOutboxDrainService(outboxPort, redeliveryPort, BATCH_SIZE, MAX_RETRIES);
        TenantContextHolder.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static PendingFact fact(String type, int retries) {
        return new PendingFact(
                UUID.randomUUID(),
                UUID.randomUUID(),
                type,
                "{\"factType\":\"" + type + "\"}",
                retries);
    }

    @Test
    @DisplayName("fato aceito pelo HIST sai da tabela como 'sent'")
    void deliveredFactIsMarkedSent() {
        PendingFact entry = fact("evento.criado", 0);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(entry));
        when(redeliveryPort.redeliver(entry.payloadJson())).thenReturn(true);

        DrainResult result = service.drainCurrentTenant();

        verify(outboxPort).markSent(entry.id());
        assertThat(result).isEqualTo(new DrainResult(1, 1, 0));
    }

    @Test
    @DisplayName("HIST fora do ar para o lote — não queima o orçamento de retry dos demais")
    void outageStopsTheBatch() {
        PendingFact first = fact("evento.criado", 0);
        PendingFact second = fact("evento.confirmado", 0);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(first, second));
        when(redeliveryPort.redeliver(first.payloadJson())).thenReturn(false);

        DrainResult result = service.drainCurrentTenant();

        verify(outboxPort).markRetried(eq(first.id()), anyString());
        // Tentar o segundo contra uma indisponibilidade já conhecida é como um problema
        // transitório vira permanente: os dois esgotariam as tentativas juntos.
        verify(redeliveryPort, never()).redeliver(second.payloadJson());
        verify(outboxPort, never()).markSent(second.id());
        assertThat(result.delivered()).isZero();
    }

    @Test
    @DisplayName("esgotadas as tentativas, vira 'failed' e para de ser tentado")
    void exhaustedRetriesBecomeTerminal() {
        PendingFact exhausted = fact("evento.criado", MAX_RETRIES);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(exhausted));

        DrainResult result = service.drainCurrentTenant();

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

        assertThat(service.drainCurrentTenant()).isEqualTo(new DrainResult(0, 0, 0));
        verifyNoInteractions(redeliveryPort);
    }

    @Test
    @DisplayName("sem tenant vinculado, falha em vez de varrer o schema global")
    void requiresTenantBinding() {
        TenantContextHolder.clear();

        assertThatThrownBy(() -> service.drainCurrentTenant())
                .as(
                        "um sweep sem tenant leria o schema global, não acharia a tabela e reportaria sucesso")
                .isInstanceOf(RuntimeException.class);
    }
}
