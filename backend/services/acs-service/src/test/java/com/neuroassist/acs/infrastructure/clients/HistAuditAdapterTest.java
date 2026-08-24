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

    private HistAuditAdapter adapter;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID cuidadorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new HistAuditAdapter(client);
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
