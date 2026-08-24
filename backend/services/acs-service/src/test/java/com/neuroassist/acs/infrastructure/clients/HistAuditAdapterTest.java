package com.neuroassist.acs.infrastructure.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.application.port.out.HistAuditPort.AuditFact;
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
 * E2E-019 — o adapter que era um {@code log.debug} com um TODO.
 *
 * <p>O que se assegura aqui não é o caminho feliz, mas os limites: que um fato sem tenant não é
 * inventado, que a autenticação nunca cai por causa da auditoria, e que credencial nenhuma entra no
 * payload.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HistAuditAdapter — fatos de autenticação no hist-service")
class HistAuditAdapterTest {

    @Mock private HistAuditClient client;
    @Mock private com.neuroassist.acs.application.port.out.HistAuditOutboxPort outboxPort;

    private HistAuditAdapter adapter;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID cuidadorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter =
                new HistAuditAdapter(
                        client,
                        outboxPort,
                        // JavaTimeModule porque occurredAt e um Instant: um ObjectMapper cru
                        // falharia ao serializar, a excecao seria capturada, e o fato se perderia
                        // — com log diferente e resultado igual ao de antes do outbox.
                        com.fasterxml.jackson.databind.json.JsonMapper.builder()
                                .addModule(
                                        new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                                .build());
    }

    private AuditFact loginOk() {
        return new AuditFact(
                "autenticacao.realizada",
                tenantId,
                cuidadorId,
                "corr-1",
                Instant.parse("2026-08-23T12:00:00Z"),
                Map.of("login", "coord@clinica.dev", "papel", "COORDENADOR_CLINICA"));
    }

    @Test
    @DisplayName("envia o fato com subject cuidador e sem escopo de paciente")
    void sendsTheFactWithCaregiverSubject() {
        when(client.recordFact(any())).thenReturn(null);

        adapter.recordFact(loginOk());

        ArgumentCaptor<HistAuditClient.RecordAuditFactRequest> sent =
                ArgumentCaptor.forClass(HistAuditClient.RecordAuditFactRequest.class);
        verify(client).recordFact(sent.capture());

        assertThat(sent.getValue().factType()).isEqualTo("autenticacao.realizada");
        assertThat(sent.getValue().subject().type()).isEqualTo("cuidador");
        assertThat(sent.getValue().subject().id()).isEqualTo(cuidadorId);
        assertThat(sent.getValue().factId()).as("chave de idempotência do HIST").isNotNull();
        // Fato de autenticação não é sobre paciente: sem escopo, só legível com hist:read:all.
        assertThat(sent.getValue().scopePacienteId()).isNull();
    }

    @Test
    @DisplayName("fato sem tenant não é enviado — não se inventa empresa de destino")
    void factWithoutTenantIsNotSent() {
        AuditFact noTenant =
                new AuditFact(
                        "autenticacao.falhou",
                        null,
                        null,
                        "corr-2",
                        Instant.now(),
                        Map.of("login", "desconhecido@lugar.nenhum"));

        adapter.recordFact(noTenant);

        // Atribuir a um tenant qualquer deixaria um desconhecido escrever na trilha de uma
        // empresa só chutando logins.
        verify(client, never()).recordFact(any());
    }

    @Test
    @DisplayName("HIST fora do ar NÃO derruba a autenticação")
    void histOutageNeverBreaksAuthentication() {
        doThrow(new RuntimeException("connection refused")).when(client).recordFact(any());

        // Propagar aqui impediria a cuidadora de entrar no painel porque a auditoria caiu — o
        // oposto do que fire-and-forget significa.
        assertThatCode(() -> adapter.recordFact(loginOk())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HIST fora do ar → fato vai para o outbox, com o mesmo factId e o tenant certo")
    void outageQueuesTheFact() {
        doThrow(new RuntimeException("connection refused")).when(client).recordFact(any());

        adapter.recordFact(loginOk());

        ArgumentCaptor<UUID> factId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        verify(outboxPort)
                .enqueue(
                        factId.capture(),
                        org.mockito.ArgumentMatchers.eq("autenticacao.realizada"),
                        tenant.capture(),
                        org.mockito.ArgumentMatchers.anyString());

        assertThat(tenant.getValue()).isEqualTo(tenantId);
        assertThat(factId.getValue()).isNotNull();
    }

    @Test
    @DisplayName("falha ao enfileirar NÃO derruba a autenticação")
    void queueFailureNeverBreaksAuthentication() {
        doThrow(new RuntimeException("hist down")).when(client).recordFact(any());
        doThrow(new RuntimeException("outbox insert failed"))
                .when(outboxPort)
                .enqueue(
                        any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        any(),
                        org.mockito.ArgumentMatchers.anyString());

        assertThatCode(() -> adapter.recordFact(loginOk())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("o payload não carrega senha nem hash")
    void payloadCarriesNoCredential() {
        when(client.recordFact(any())).thenReturn(null);

        adapter.recordFact(loginOk());

        ArgumentCaptor<HistAuditClient.RecordAuditFactRequest> sent =
                ArgumentCaptor.forClass(HistAuditClient.RecordAuditFactRequest.class);
        verify(client).recordFact(sent.capture());

        assertThat(sent.getValue().payload().keySet())
                .as("o payload do HIST é cifrado, mas isso não é licença para pôr credencial nele")
                .doesNotContain("senha", "password", "credentialHash", "hash");
    }
}
