package com.neuroassist.gest.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.gest.api.dto.DetectionProfilePrimitiveResponse;
import com.neuroassist.gest.api.dto.DetectionProfilePrimitiveResponse.Origem;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.application.port.out.EdgeDevicePort;
import com.neuroassist.gest.application.port.out.EdgeDevicePort.DeviceBinding;
import com.neuroassist.gest.application.port.out.PatientCatalogPort;
import com.neuroassist.gest.application.port.out.PatientCatalogPort.PatientBands;
import com.neuroassist.gest.application.port.out.TenantContextPort;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.DetectionThresholdTable;
import com.neuroassist.gest.domain.model.Sensibilidade;
import com.neuroassist.gest.domain.service.DetectionProfileDomainService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * A projeção do catálogo do paciente sobre o perfil da empresa (E2E-034).
 *
 * <p>Este método devolvia {@code Optional.empty()} sempre — e é o endpoint que o EDGE chama no
 * heartbeat, então o ponteiro de perfil do EDGE lia "sem perfil ativo" para todo dispositivo desde
 * sempre. Estes testes existem para que ele não volte a mentir.
 */
@ExtendWith(MockitoExtension.class)
class DetectionProfileForDeviceTest {

    @Mock private DetectionProfileRepositoryPort profileRepository;
    @Mock private DetectionProfilePrimitiveRepositoryPort primitiveRepository;
    @Mock private TipoGestoRepositoryPort tipoGestoRepository;
    @Mock private DetectionProfileDomainService domainService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TenantContextPort tenantContextPort;
    @Mock private EdgeDevicePort edgeDevicePort;
    @Mock private PatientCatalogPort patientCatalogPort;

    @InjectMocks private DetectionProfileService service;

    private static final UUID DEVICE = UUID.randomUUID();
    private static final UUID PACIENTE = UUID.randomUUID();
    private static final UUID PISCAR = UUID.randomUUID();
    private static final UUID SOBRANCELHA = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();

    // ── fixtures ─────────────────────────────────────────────────────────────

    private void perfilAtivoComDoisGestos() {
        DetectionProfile profile =
                new DetectionProfile(
                        PROFILE_ID,
                        7L,
                        true,
                        "v1.1.0",
                        DetectionThresholdTable.defaults(),
                        Instant.now(),
                        null,
                        "t1");
        when(profileRepository.findActive()).thenReturn(Optional.of(profile));
        when(primitiveRepository.findByProfileId(PROFILE_ID))
                .thenReturn(
                        List.of(
                                primitivo(PISCAR, "Piscar duplo"),
                                primitivo(SOBRANCELHA, "Sobrancelha dupla")));
    }

    private static DetectionProfilePrimitive primitivo(UUID tipoGestoId, String nome) {
        return new DetectionProfilePrimitive(
                UUID.randomUUID(),
                PROFILE_ID,
                tipoGestoId,
                nome,
                "ref/" + tipoGestoId,
                Sensibilidade.media);
    }

    private static DetectionProfilePrimitiveResponse gesto(
            DetectionProfileResponse response, UUID tipoGestoId) {
        return response.primitives().stream()
                .filter(p -> p.tipoGestoId().equals(tipoGestoId))
                .findFirst()
                .orElseThrow();
    }

    // ── o caminho feliz ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("com paciente pareado e catálogo lido")
    class ComPaciente {

        @Test
        void gesto_calibrado_usa_a_faixa_do_paciente_e_diz_de_onde_veio() {
            perfilAtivoComDoisGestos();
            when(edgeDevicePort.resolveBinding(DEVICE))
                    .thenReturn(DeviceBinding.of("t1", PACIENTE));
            when(patientCatalogPort.bandsFor(PACIENTE))
                    .thenReturn(PatientBands.of(4, Map.of(PISCAR, Sensibilidade.alta)));

            DetectionProfileResponse response = service.findForDevice(DEVICE).orElseThrow();

            assertThat(gesto(response, PISCAR).sensibilidade()).isEqualTo(Sensibilidade.alta);
            assertThat(gesto(response, PISCAR).origem()).isEqualTo(Origem.PACIENTE);
            assertThat(response.pacienteId()).isEqualTo(PACIENTE);
            assertThat(response.catalogoDegradado()).isFalse();
        }

        @Test
        void gesto_sem_comando_fica_com_a_faixa_da_empresa_e_isso_e_visivel() {
            // "media porque a cuidadora escolheu media" e "media porque ninguem calibrou este
            // gesto" nao podem parecer iguais — e a mesma licao da ProfilePointer de tres estados.
            perfilAtivoComDoisGestos();
            when(edgeDevicePort.resolveBinding(DEVICE))
                    .thenReturn(DeviceBinding.of("t1", PACIENTE));
            when(patientCatalogPort.bandsFor(PACIENTE))
                    .thenReturn(PatientBands.of(4, Map.of(PISCAR, Sensibilidade.alta)));

            DetectionProfileResponse response = service.findForDevice(DEVICE).orElseThrow();

            assertThat(gesto(response, SOBRANCELHA).sensibilidade()).isEqualTo(Sensibilidade.media);
            assertThat(gesto(response, SOBRANCELHA).origem()).isEqualTo(Origem.PERFIL_EMPRESA);
        }

        @Test
        void etag_combina_perfil_e_catalogo() {
            perfilAtivoComDoisGestos();
            when(edgeDevicePort.resolveBinding(DEVICE))
                    .thenReturn(DeviceBinding.of("t1", PACIENTE));
            when(patientCatalogPort.bandsFor(PACIENTE)).thenReturn(PatientBands.of(4, Map.of()));

            assertThat(service.findForDevice(DEVICE).orElseThrow().etag()).isEqualTo("\"7-4\"");
        }

        @Test
        void recalibrar_o_paciente_muda_o_etag_mesmo_com_o_perfil_parado() {
            // O ponto critico. So profileVersion faria o GEST responder 304 depois de a cuidadora
            // trocar media por alta, e o aparelho seguiria com a faixa velha por tempo
            // indeterminado.
            perfilAtivoComDoisGestos();
            when(edgeDevicePort.resolveBinding(DEVICE))
                    .thenReturn(DeviceBinding.of("t1", PACIENTE));
            when(patientCatalogPort.bandsFor(PACIENTE))
                    .thenReturn(PatientBands.of(4, Map.of(PISCAR, Sensibilidade.media)))
                    .thenReturn(PatientBands.of(5, Map.of(PISCAR, Sensibilidade.alta)));

            String antes = service.findForDevice(DEVICE).orElseThrow().etag();
            String depois = service.findForDevice(DEVICE).orElseThrow().etag();

            assertThat(antes).isEqualTo("\"7-4\"");
            assertThat(depois).isEqualTo("\"7-5\"");
            assertThat(antes).isNotEqualTo(depois);
        }
    }

    // ── degradação: o aparelho está na casa do paciente ──────────────────────

    @Nested
    @DisplayName("degradação")
    class Degradacao {

        @Test
        void pac_mudo_serve_a_faixa_padrao_e_diz_que_esta_degradado() {
            perfilAtivoComDoisGestos();
            when(edgeDevicePort.resolveBinding(DEVICE))
                    .thenReturn(DeviceBinding.of("t1", PACIENTE));
            when(patientCatalogPort.bandsFor(PACIENTE)).thenReturn(PatientBands.unavailable());

            DetectionProfileResponse response = service.findForDevice(DEVICE).orElseThrow();

            assertThat(response.primitives()).isNotEmpty();
            assertThat(gesto(response, PISCAR).sensibilidade()).isEqualTo(Sensibilidade.media);
            assertThat(gesto(response, PISCAR).origem()).isEqualTo(Origem.INDISPONIVEL);
            assertThat(response.catalogoDegradado()).isTrue();
            assertThat(response.catalogoVersao()).isNull();
        }

        @Test
        void paciente_sem_catalogo_nao_e_degradacao() {
            // O PAC RESPONDEU: este paciente nao tem catalogo publicado. Estado legitimo do
            // sistema. Colapsar os dois no mesmo sinal foi o que impediu HB-04 de passar no EDGE.
            perfilAtivoComDoisGestos();
            when(edgeDevicePort.resolveBinding(DEVICE))
                    .thenReturn(DeviceBinding.of("t1", PACIENTE));
            when(patientCatalogPort.bandsFor(PACIENTE)).thenReturn(PatientBands.empty());

            DetectionProfileResponse response = service.findForDevice(DEVICE).orElseThrow();

            assertThat(response.catalogoDegradado()).isFalse();
            assertThat(gesto(response, PISCAR).origem()).isEqualTo(Origem.PERFIL_EMPRESA);
        }

        @Test
        void edge_mudo_serve_o_perfil_da_empresa_e_nem_chama_o_pac() {
            perfilAtivoComDoisGestos();
            when(edgeDevicePort.resolveBinding(DEVICE)).thenReturn(DeviceBinding.unknown());

            DetectionProfileResponse response = service.findForDevice(DEVICE).orElseThrow();

            assertThat(response.primitives()).hasSize(2);
            assertThat(response.pacienteId()).isNull();
            assertThat(response.catalogoDegradado()).isTrue();
            verify(patientCatalogPort, never()).bandsFor(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void dispositivo_sem_paciente_pareado_nao_e_degradacao() {
            perfilAtivoComDoisGestos();
            when(edgeDevicePort.resolveBinding(DEVICE)).thenReturn(DeviceBinding.unpaired("t1"));

            DetectionProfileResponse response = service.findForDevice(DEVICE).orElseThrow();

            assertThat(response.catalogoDegradado()).isFalse();
            assertThat(response.pacienteId()).isNull();
        }
    }

    // ── ausência de perfil ───────────────────────────────────────────────────

    @Test
    void empresa_sem_perfil_publicado_devolve_vazio_sem_perguntar_a_ninguem() {
        when(profileRepository.findActive()).thenReturn(Optional.empty());

        assertThat(service.findForDevice(DEVICE)).isEmpty();

        verify(edgeDevicePort, never()).resolveBinding(org.mockito.ArgumentMatchers.any());
        verify(patientCatalogPort, never()).bandsFor(org.mockito.ArgumentMatchers.any());
    }
}
