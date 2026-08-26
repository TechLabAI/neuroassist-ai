package com.neuroassist.evnt.infrastructure.clients.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.application.port.out.HistOutboxPort;
import com.neuroassist.evnt.infrastructure.clients.AuditTrailClient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * E2E-017 — the failure branch is what this test is about.
 *
 * <p>The adapter used to log {@code "was NOT recorded and is lost — evnt-service has no outbox"}
 * and return. Fail-open was right; forgetting was not. These are the facts of an alarm.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditTrailFeignAdapter — fato sobrevive a uma indisponibilidade do HIST")
class AuditTrailFeignAdapterTest {

    @Mock private AuditTrailClient client;
    @Mock private HistOutboxPort outboxPort;

    private AuditTrailFeignAdapter adapter;

    private final UUID factId = UUID.randomUUID();
    private final UUID pacienteId = UUID.randomUUID();

    /**
     * O mapper precisa do {@code JavaTimeModule} porque {@code occurredAt} é um {@link Instant}.
     *
     * <p>Não é detalhe de teste: um {@code new ObjectMapper()} cru falha ao serializar o request, a
     * exceção é capturada em {@code enqueueForRetry} e o fato se perde exatamente como antes — com
     * um log diferente. O mapper injetado pelo Spring já registra o módulo, e este teste tem de
     * usar o mesmo, sob pena de exercitar um caminho que produção não percorre.
     */
    private static ObjectMapper mapper() {
        return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .build();
    }

    @BeforeEach
    void setUp() {
        adapter = new AuditTrailFeignAdapter(client, outboxPort, mapper());
    }

    private AuditTrailPort.AuditFact anAlarmFact() {
        return new AuditTrailPort.AuditFact(
                factId,
                "evento.criado",
                "paciente",
                pacienteId,
                UUID.randomUUID(),
                "Cuidadora Dev",
                Map.of("urgencia", "emergencia"),
                Instant.parse("2026-08-23T12:00:00Z"));
    }

    @Test
    @DisplayName("entrega direta bem-sucedida não enfileira nada")
    void successfulDeliveryDoesNotQueue() {
        when(client.recordFact(any())).thenReturn(null);

        adapter.record(anAlarmFact());

        verify(outboxPort, never()).enqueue(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("HIST fora do ar → fato vai para o outbox, com o MESMO factId")
    void outageQueuesTheFactWithTheSameFactId() throws Exception {
        doThrow(new RuntimeException("connection refused")).when(client).recordFact(any());

        adapter.record(anAlarmFact());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outboxPort).enqueue(eq(factId), eq("evento.criado"), payload.capture());

        // O factId gravado tem de ser o mesmo que a tentativa original enviou: é o que faz o
        // hist-service tratar a reentrega como replay (200) em vez de um segundo fato (201).
        var stored =
                mapper().readValue(
                                payload.getValue(), AuditTrailClient.RecordAuditFactRequest.class);
        org.assertj.core.api.Assertions.assertThat(stored.factId()).isEqualTo(factId);
        org.assertj.core.api.Assertions.assertThat(stored.factType()).isEqualTo("evento.criado");
        org.assertj.core.api.Assertions.assertThat(stored.scopePacienteId()).isEqualTo(pacienteId);
    }

    @Test
    @DisplayName("falha ao enfileirar NÃO derruba a operação de negócio (AUD-10)")
    void queueFailureNeverBreaksTheBusinessOperation() {
        doThrow(new RuntimeException("hist down")).when(client).recordFact(any());
        doThrow(new RuntimeException("outbox insert failed"))
                .when(outboxPort)
                .enqueue(any(), anyString(), anyString());

        // Propagar aqui abortaria a transação do chamador — a confirmação de uma cuidadora
        // seria desfeita porque a auditoria falhou. É o contrário do que AUD-10 exige.
        assertThatCode(() -> adapter.record(anAlarmFact())).doesNotThrowAnyException();
    }
}
