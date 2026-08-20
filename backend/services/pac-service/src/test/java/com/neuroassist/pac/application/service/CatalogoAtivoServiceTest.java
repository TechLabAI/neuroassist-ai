package com.neuroassist.pac.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.neuroassist.pac.application.port.in.CatalogoAtivoUseCase.CatalogoAtivoResult;
import com.neuroassist.pac.application.port.out.ComandoRepositoryPort;
import com.neuroassist.pac.application.port.out.PacienteRepositoryPort;
import com.neuroassist.pac.application.port.out.PacienteVersionBumper;
import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.domain.model.EstadoComando;
import com.neuroassist.pac.domain.model.Paciente;
import com.neuroassist.pac.domain.model.Sensibilidade;
import com.neuroassist.pac.domain.model.Urgencia;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import com.neuroassist.pac.shared.error.CodedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PAC-020 — Unit tests for CatalogoAtivoService.
 *
 * <p>Acceptance criteria verified:
 *
 * <ul>
 *   <li>Returns only published Comandos.
 *   <li>Correct catalogoVersao as ETag seed.
 *   <li>Patient not found → CodedException(not_found.paciente).
 *   <li>Empty catalog (no published commands) → empty list + version.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CatalogoAtivoServiceTest {

    private static final String TENANT_ID = "empresa-abc";
    private static final UUID PACIENTE_ID = UUID.randomUUID();

    @Mock private ComandoRepositoryPort comandoRepository;

    @Mock private PacienteRepositoryPort pacienteRepository;

    @Mock private PacienteVersionBumper pacienteVersionBumper;

    @InjectMocks private CatalogoAtivoService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Paciente makePaciente(int catalogoVersao) {
        return new Paciente(
                PACIENTE_ID,
                TENANT_ID,
                "Marcio",
                null,
                List.of(),
                1,
                catalogoVersao,
                Instant.now(),
                Instant.now());
    }

    private Comando makePublishedComando() {
        return new Comando(
                UUID.randomUUID(),
                PACIENTE_ID,
                "Levantar mao",
                "hidratacao",
                UUID.randomUUID(),
                null,
                Sensibilidade.media,
                200,
                800,
                Urgencia.rotina,
                EstadoComando.published,
                0,
                1,
                Instant.now(),
                Instant.now());
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getCatalogoAtivo: returns published commands + catalogoVersao as ETag seed")
    void getCatalogoAtivo_returnsPublishedAndVersion() {
        Paciente paciente = makePaciente(3);
        when(pacienteRepository.findByIdAndTenantId(PACIENTE_ID, TENANT_ID))
                .thenReturn(Optional.of(paciente));

        Comando cmd1 = makePublishedComando();
        when(comandoRepository.findPublishedByPacienteId(PACIENTE_ID)).thenReturn(List.of(cmd1));
        when(pacienteVersionBumper.getCatalogoVersao(PACIENTE_ID)).thenReturn(3);

        CatalogoAtivoResult result = service.getCatalogoAtivo(PACIENTE_ID);

        assertThat(result.comandos()).hasSize(1);
        assertThat(result.catalogoVersao()).isEqualTo(3);
    }

    @Test
    @DisplayName("getCatalogoAtivo: empty catalog returns empty list + current version")
    void getCatalogoAtivo_nonePublished_returnsEmptyList() {
        when(pacienteRepository.findByIdAndTenantId(PACIENTE_ID, TENANT_ID))
                .thenReturn(Optional.of(makePaciente(0)));
        when(comandoRepository.findPublishedByPacienteId(PACIENTE_ID)).thenReturn(List.of());
        when(pacienteVersionBumper.getCatalogoVersao(PACIENTE_ID)).thenReturn(0);

        CatalogoAtivoResult result = service.getCatalogoAtivo(PACIENTE_ID);

        assertThat(result.comandos()).isEmpty();
        assertThat(result.catalogoVersao()).isZero();
    }

    @Test
    @DisplayName("getCatalogoAtivo: patient not in tenant → CodedException(not_found.paciente)")
    void getCatalogoAtivo_patientNotFound_throwsCoded() {
        when(pacienteRepository.findByIdAndTenantId(PACIENTE_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCatalogoAtivo(PACIENTE_ID))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("not_found.paciente"));
    }

    @Test
    @DisplayName(
            "getCatalogoAtivo: ETag derived from catalogoVersao enables 304 logic in controller")
    void getCatalogoAtivo_etagMatchesCatalogoVersao() {
        int expectedVersion = 7;
        when(pacienteRepository.findByIdAndTenantId(PACIENTE_ID, TENANT_ID))
                .thenReturn(Optional.of(makePaciente(expectedVersion)));
        when(comandoRepository.findPublishedByPacienteId(PACIENTE_ID)).thenReturn(List.of());
        when(pacienteVersionBumper.getCatalogoVersao(PACIENTE_ID)).thenReturn(expectedVersion);

        CatalogoAtivoResult result = service.getCatalogoAtivo(PACIENTE_ID);

        assertThat(result.catalogoVersao()).isEqualTo(expectedVersion);
        // Controller converts this to ETag: String.valueOf(catalogoVersao) == "7"
        assertThat(String.valueOf(result.catalogoVersao())).isEqualTo("7");
    }
}
