package com.neuroassist.pac.infrastructure.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.neuroassist.pac.application.port.out.AcsLinkPort.Outcome;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import feign.Request;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * E2E-015 — classification of acs-service failures.
 *
 * <p>The property under test is the one the EDGE heartbeat lacked: a 403 must not look like an
 * outage. There, a fallback collapsed every failure into "the upstream is degraded", so an
 * authorization defect was retried silently for a whole session instead of being reported once.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AcsLinkAdapter — recusa do ACS × ACS indisponível")
class AcsLinkAdapterTest {

    private static final String TENANT = "empresa-abc";

    @Mock private AcsLinkClient client;

    private AcsLinkAdapter adapter;

    private final UUID paciente = UUID.randomUUID();
    private final UUID cuidador = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new AcsLinkAdapter(client);
        TenantContextHolder.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static feign.FeignException feignStatus(int status) {
        Request request =
                Request.create(
                        Request.HttpMethod.POST,
                        "/api/v1/acs/caregivers/x/patients/y/link",
                        Map.of(),
                        new byte[0],
                        StandardCharsets.UTF_8,
                        new RequestTemplate());
        return feign.FeignException.errorStatus(
                "AcsLinkClient#createLink",
                feign.Response.builder()
                        .status(status)
                        .reason("test")
                        .request(request)
                        .headers(Map.of())
                        .build());
    }

    @Test
    @DisplayName("link aceito → ACCEPTED, com o tenant da thread no request")
    void linkAcceptedCarriesTheThreadTenant() {
        assertThat(adapter.link(paciente, cuidador)).isEqualTo(Outcome.ACCEPTED);
        verify(client).createLink(cuidador, paciente, TENANT);
    }

    @Test
    @DisplayName("409 no unlink (último vínculo ativo, D-204) → REFUSED, não RETRY")
    void lastActiveLinkConflictIsTerminal() {
        doThrow(feignStatus(409)).when(client).revokeLink(cuidador, paciente);

        // Reentregar isto para sempre esconderia uma recusa permanente atrás de um contador
        // que só cresce — o ACS vai responder 409 identicamente na próxima tentativa.
        assertThat(adapter.unlink(paciente, cuidador)).isEqualTo(Outcome.REFUSED);
    }

    @Test
    @DisplayName("403 → REFUSED: erro de autorização não é degradação de serviço")
    void forbiddenIsRefusedNotRetry() {
        doThrow(feignStatus(403)).when(client).createLink(any(), any(), any());

        assertThat(adapter.link(paciente, cuidador)).isEqualTo(Outcome.REFUSED);
    }

    @Test
    @DisplayName("422 paciente desconhecido → REFUSED")
    void unprocessableIsRefused() {
        doThrow(feignStatus(422)).when(client).createLink(any(), any(), any());

        assertThat(adapter.link(paciente, cuidador)).isEqualTo(Outcome.REFUSED);
    }

    @Test
    @DisplayName("503 → RETRY: o ACS voltou a ficar indisponível, a entrada continua pendente")
    void serviceUnavailableIsRetryable() {
        doThrow(feignStatus(503)).when(client).createLink(any(), any(), any());

        assertThat(adapter.link(paciente, cuidador)).isEqualTo(Outcome.RETRY);
    }

    @Test
    @DisplayName("timeout/conexão recusada → RETRY")
    void networkFailureIsRetryable() {
        doThrow(new RuntimeException("connect timed out"))
                .when(client)
                .createLink(any(), any(), any());

        assertThat(adapter.link(paciente, cuidador)).isEqualTo(Outcome.RETRY);
    }

    @Test
    @DisplayName("nunca propaga exceção — o lote decide, não o adapter")
    void neverThrows() {
        doThrow(new RuntimeException("boom")).when(client).revokeLink(eq(cuidador), eq(paciente));

        assertThat(adapter.unlink(paciente, cuidador)).isEqualTo(Outcome.RETRY);
    }
}
