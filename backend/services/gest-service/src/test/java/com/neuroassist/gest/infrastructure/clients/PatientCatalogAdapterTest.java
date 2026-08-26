package com.neuroassist.gest.infrastructure.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.neuroassist.gest.application.port.out.PatientCatalogPort.PatientBands;
import com.neuroassist.gest.domain.model.Sensibilidade;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tradução do catálogo do PAC em faixas por primitivo (E2E-034). */
@ExtendWith(MockitoExtension.class)
class PatientCatalogAdapterTest {

    @org.junit.jupiter.api.BeforeEach
    void bindTenant() {
        com.neuroassist.gest.infrastructure.security.TenantContextHolder.set(TENANT);
    }

    @org.junit.jupiter.api.AfterEach
    void clearTenant() {
        com.neuroassist.gest.infrastructure.security.TenantContextHolder.clear();
    }

    @Mock private PacCatalogClient client;
    @InjectMocks private PatientCatalogAdapter adapter;

    private static final String TENANT = "e2e00000-0000-0000-0000-000000000001";
    private static final UUID PACIENTE = UUID.randomUUID();
    private static final UUID PISCAR = UUID.randomUUID();

    private static PacCatalogClient.ComandoView comando(UUID primitivo, String faixa) {
        return new PacCatalogClient.ComandoView(UUID.randomUUID(), primitivo, faixa, 1);
    }

    private static FeignException feign(int status) {
        Request request =
                Request.create(
                        HttpMethod.GET,
                        "/api/pac/v1/pacientes/x/catalogo-ativo",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        new RequestTemplate());
        return FeignException.errorStatus(
                "getCatalogoAtivo",
                feign.Response.builder().status(status).request(request).build());
    }

    @Test
    void traduz_comandos_em_faixas_por_primitivo() {
        when(client.getCatalogoAtivo(PACIENTE, TENANT))
                .thenReturn(
                        new PacCatalogClient.CatalogoAtivoView(
                                PACIENTE, 4, List.of(comando(PISCAR, "alta"))));

        PatientBands bands = adapter.bandsFor(PACIENTE);

        assertThat(bands.porPrimitivo()).containsEntry(PISCAR, Sensibilidade.alta);
        assertThat(bands.catalogoVersao()).isEqualTo(4);
        assertThat(bands.degraded()).isFalse();
    }

    @Test
    void dois_comandos_no_mesmo_primitivo_vence_o_mais_sensivel() {
        // Um paciente pode ter dois comandos apontando para o mesmo gesto. Entre alarme falso e
        // pedido de ajuda que nao sai, o pedido que nao sai e o pior dos dois.
        when(client.getCatalogoAtivo(PACIENTE, TENANT))
                .thenReturn(
                        new PacCatalogClient.CatalogoAtivoView(
                                PACIENTE,
                                4,
                                List.of(comando(PISCAR, "baixa"), comando(PISCAR, "alta"))));

        assertThat(adapter.bandsFor(PACIENTE).porPrimitivo())
                .containsEntry(PISCAR, Sensibilidade.alta);
    }

    @Test
    void faixa_fora_do_vocabulario_cai_no_padrao_em_vez_de_explodir() {
        // Se os dois lados divergirem de vocabulario, isso nao pode derrubar a deteccao do
        // paciente — o primitivo simplesmente nao entra no mapa e herda a faixa do perfil.
        when(client.getCatalogoAtivo(PACIENTE, TENANT))
                .thenReturn(
                        new PacCatalogClient.CatalogoAtivoView(
                                PACIENTE, 4, List.of(comando(PISCAR, "turbo"))));

        PatientBands bands = adapter.bandsFor(PACIENTE);

        assertThat(bands.porPrimitivo()).isEmpty();
        assertThat(bands.degraded()).isFalse();
    }

    @Test
    void paciente_sem_catalogo_404_nao_e_degradacao() {
        when(client.getCatalogoAtivo(PACIENTE, TENANT)).thenThrow(feign(404));

        PatientBands bands = adapter.bandsFor(PACIENTE);

        assertThat(bands.degraded()).isFalse();
        assertThat(bands.catalogoVersao()).isZero();
    }

    @Test
    void catalogo_vazio_nao_e_degradacao() {
        when(client.getCatalogoAtivo(PACIENTE, TENANT))
                .thenReturn(new PacCatalogClient.CatalogoAtivoView(PACIENTE, 0, List.of()));

        assertThat(adapter.bandsFor(PACIENTE).degraded()).isFalse();
    }

    @Test
    void falha_de_transporte_propaga_para_o_breaker_contar() {
        // O ponto: se o adapter capturasse isto no corpo do metodo e devolvesse unavailable(), o
        // resilience4j registraria SUCESSO e o breaker nunca abriria — um breaker decorativo.
        // Quem traduz em unavailable() e o fallbackMethod, depois de o breaker ter contado.
        when(client.getCatalogoAtivo(PACIENTE, TENANT)).thenThrow(feign(503));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> adapter.bandsFor(PACIENTE)))
                .isInstanceOf(FeignException.class);
    }
}
