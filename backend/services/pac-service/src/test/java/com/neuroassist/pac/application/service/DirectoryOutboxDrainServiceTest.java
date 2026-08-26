package com.neuroassist.pac.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neuroassist.pac.application.port.in.DirectoryOutboxDrainUseCase.DrainResult;
import com.neuroassist.pac.application.port.out.AcsLinkPort;
import com.neuroassist.pac.application.port.out.DirectoryOutboxPort;
import com.neuroassist.pac.application.port.out.DirectoryOutboxPort.PendingEntry;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
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
 * E2E-015 — the drain that makes the directory outbox mean something.
 *
 * <p>These tests exist because the equivalent code for the audit outbox went a whole release
 * without one: the table was written correctly, nothing read it back, and the rows' presence made
 * the queue look healthy (C-44). What is asserted here is therefore not "the happy path works" but
 * the three properties that were missing then — entries leave the table, ordering survives an
 * outage, and giving up is distinguishable from being busy.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DirectoryOutboxDrainService — propagação do vínculo para o directory do ACS")
class DirectoryOutboxDrainServiceTest {

    private static final String TENANT = "empresa-abc";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 10;

    @Mock private DirectoryOutboxPort outboxPort;
    @Mock private AcsLinkPort acsLinkPort;

    private DirectoryOutboxDrainService service;

    private final UUID paciente = UUID.randomUUID();
    private final UUID cuidadorA = UUID.randomUUID();
    private final UUID cuidadorB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DirectoryOutboxDrainService(outboxPort, acsLinkPort, BATCH_SIZE, MAX_RETRIES);
        TenantContextHolder.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private PendingEntry link(UUID cuidador, int retries) {
        return new PendingEntry(UUID.randomUUID(), "LINK", paciente, cuidador, retries);
    }

    private PendingEntry unlink(UUID cuidador, int retries) {
        return new PendingEntry(UUID.randomUUID(), "UNLINK", paciente, cuidador, retries);
    }

    @Test
    @DisplayName("entrada aceita pelo ACS sai da tabela como 'sent'")
    void acceptedEntryIsMarkedSent() {
        PendingEntry entry = link(cuidadorA, 0);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(entry));
        when(acsLinkPort.link(paciente, cuidadorA)).thenReturn(AcsLinkPort.Outcome.ACCEPTED);

        DrainResult result = service.drainCurrentTenant();

        verify(outboxPort).markSent(entry.id());
        assertThat(result).isEqualTo(new DrainResult(1, 1, 0));
    }

    @Test
    @DisplayName("UNLINK chama revoke, não create — a operação gravada é a que é executada")
    void unlinkCallsRevoke() {
        PendingEntry entry = unlink(cuidadorA, 0);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(entry));
        when(acsLinkPort.unlink(paciente, cuidadorA)).thenReturn(AcsLinkPort.Outcome.ACCEPTED);

        service.drainCurrentTenant();

        verify(acsLinkPort).unlink(paciente, cuidadorA);
        verify(acsLinkPort, never()).link(any(), any());
    }

    @Test
    @DisplayName("falha transitória para o lote — a ordem LINK→UNLINK do mesmo par é preservada")
    void transientFailureStopsTheBatchToPreserveOrder() {
        PendingEntry first = link(cuidadorA, 0);
        PendingEntry second = unlink(cuidadorA, 0);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(first, second));
        when(acsLinkPort.link(paciente, cuidadorA)).thenReturn(AcsLinkPort.Outcome.RETRY);

        DrainResult result = service.drainCurrentTenant();

        verify(outboxPort).markRetried(eq(first.id()), anyString());
        // O UNLINK não pode ser entregue antes do LINK que ele desfaz: entregue fora de ordem,
        // o directory ficaria com um vínculo que o PAC já removeu — uma cuidadora continuaria
        // recebendo alarme de um paciente que não está mais sob seus cuidados.
        verify(acsLinkPort, never()).unlink(any(), any());
        verify(outboxPort, never()).markSent(second.id());
        assertThat(result.delivered()).isZero();
    }

    @Test
    @DisplayName("recusa terminal não bloqueia a fila — ela nunca será aplicada")
    void terminalRefusalDoesNotBlockTheQueue() {
        PendingEntry refused = unlink(cuidadorA, 0);
        PendingEntry next = link(cuidadorB, 0);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(refused, next));
        when(acsLinkPort.unlink(paciente, cuidadorA)).thenReturn(AcsLinkPort.Outcome.REFUSED);
        when(acsLinkPort.link(paciente, cuidadorB)).thenReturn(AcsLinkPort.Outcome.ACCEPTED);

        DrainResult result = service.drainCurrentTenant();

        verify(outboxPort).markFailed(eq(refused.id()), anyString());
        verify(outboxPort).markSent(next.id());
        assertThat(result).isEqualTo(new DrainResult(2, 1, 1));
    }

    @Test
    @DisplayName("esgotadas as tentativas, a entrada vira 'failed' e para de ser tentada")
    void exhaustedRetriesBecomeTerminal() {
        PendingEntry exhausted = link(cuidadorA, MAX_RETRIES);
        when(outboxPort.findPending(BATCH_SIZE)).thenReturn(List.of(exhausted));

        DrainResult result = service.drainCurrentTenant();

        verify(outboxPort).markFailed(eq(exhausted.id()), anyString());
        // Não tenta de novo: um estado terminal que continua chamando o ACS é indistinguível
        // de uma fila ocupada, que foi exatamente como o C-44 passou despercebido.
        verifyNoInteractions(acsLinkPort);
        assertThat(result).isEqualTo(new DrainResult(1, 0, 1));
    }

    @Test
    @DisplayName("fila vazia não chama o ACS")
    void emptyQueueIsANoOp() {
        when(outboxPort.findPending(anyInt())).thenReturn(List.of());

        DrainResult result = service.drainCurrentTenant();

        assertThat(result).isEqualTo(new DrainResult(0, 0, 0));
        verifyNoInteractions(acsLinkPort);
    }

    @Test
    @DisplayName("sem tenant vinculado, falha em vez de varrer o schema global")
    void requiresTenantBinding() {
        TenantContextHolder.clear();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.drainCurrentTenant())
                .as(
                        "um sweep sem tenant leria o schema global, não acharia a tabela e"
                                + " reportaria sucesso")
                .isInstanceOf(RuntimeException.class);
    }
}
