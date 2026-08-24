package com.neuroassist.pac.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.pac.application.port.out.AcsDirectoryPort;
import com.neuroassist.pac.application.port.out.CallerAuthoritiesPort;
import com.neuroassist.pac.application.port.out.DirectoryOutboxPort;
import com.neuroassist.pac.application.port.out.EdgeDevicePort;
import com.neuroassist.pac.application.port.out.HistErasurePort;
import com.neuroassist.pac.application.port.out.PacienteCuidadorRepositoryPort;
import com.neuroassist.pac.application.port.out.PacienteRepositoryPort;
import com.neuroassist.pac.domain.model.Paciente;
import com.neuroassist.pac.domain.model.PacienteCuidador;
import com.neuroassist.pac.domain.service.PacienteDomainService;
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

/**
 * Editar paciente e vincular cuidadoras chegam pelo mesmo {@code PUT}, e são capacidades
 * diferentes.
 *
 * <p>Sem esta separação, dar {@code pac.paciente.editar} à cuidadora daria junto o poder de se
 * vincular a qualquer paciente da empresa — a permissão de vínculo entraria de carona num campo do
 * corpo da requisição.
 */
class VinculoAuthorityTest {

    private static final String TENANT = "e2e00000-0000-0000-0000-000000000001";
    private static final UUID PACIENTE = UUID.randomUUID();
    private static final UUID CUIDADORA_A = UUID.randomUUID();
    private static final UUID CUIDADORA_B = UUID.randomUUID();

    private PacienteRepositoryPort pacienteRepository;
    private PacienteCuidadorRepositoryPort cuidadorRepository;
    private CallerAuthoritiesPort callerAuthorities;
    private PacienteService service;

    @BeforeEach
    void setUp() {
        pacienteRepository = mock(PacienteRepositoryPort.class);
        cuidadorRepository = mock(PacienteCuidadorRepositoryPort.class);
        callerAuthorities = mock(CallerAuthoritiesPort.class);
        service =
                new PacienteService(
                        pacienteRepository,
                        cuidadorRepository,
                        mock(DirectoryOutboxPort.class),
                        new PacienteDomainService(),
                        mock(EdgeDevicePort.class),
                        mock(AcsDirectoryPort.class),
                        mock(HistErasurePort.class),
                        callerAuthorities,
                        mock(com.neuroassist.pac.application.port.out.AcsLinkPort.class));
        TenantContextHolder.set(TENANT);

        Paciente p =
                new Paciente(
                        PACIENTE, TENANT, "Paciente", null, List.of(), 0, 0,
                        Instant.now(), Instant.now(), null);
        when(pacienteRepository.findByIdAndTenantId(PACIENTE, TENANT)).thenReturn(Optional.of(p));
        when(pacienteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cuidadorRepository.findByPacienteId(PACIENTE))
                .thenReturn(List.of(new PacienteCuidador(PACIENTE, CUIDADORA_A, Instant.now())));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private void chamadorPodeVincular(boolean pode) {
        when(callerAuthorities.hasAuthority(PacienteService.AUTHORITY_VINCULAR)).thenReturn(pode);
    }

    // ── sem a authority de vínculo ───────────────────────────────────────────

    @Test
    @DisplayName("cuidadora não consegue TROCAR quem cuida do paciente")
    void semAuthority_trocarVinculo_recusado() {
        chamadorPodeVincular(false);

        assertThatThrownBy(
                        () -> service.update(PACIENTE, 1, "Paciente", null, List.of(CUIDADORA_B)))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("pac.paciente.vinculo-nao-autorizado"));

        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("cuidadora não consegue ADICIONAR a si mesma ao paciente")
    void semAuthority_adicionarVinculo_recusado() {
        // O caso que motivou a separacao: sem ela, "editar paciente" daria a qualquer cuidadora o
        // poder de se vincular a qualquer paciente da empresa.
        chamadorPodeVincular(false);

        assertThatThrownBy(
                        () ->
                                service.update(
                                        PACIENTE,
                                        1,
                                        "Paciente",
                                        null,
                                        List.of(CUIDADORA_A, CUIDADORA_B)))
                .isInstanceOf(CodedException.class);
    }

    @Test
    @DisplayName("cuidadora edita o paciente reenviando a MESMA lista — não é vínculo, passa")
    void semAuthority_mesmaLista_passa() {
        // O caso comum de quem edita o nome e devolve o objeto inteiro. Recusar isto tornaria a
        // authority de edicao inutil na pratica.
        chamadorPodeVincular(false);

        assertThatCode(
                        () ->
                                service.update(
                                        PACIENTE, 1, "Nome novo", null, List.of(CUIDADORA_A)))
                .doesNotThrowAnyException();
    }

    // ── com a authority de vínculo ───────────────────────────────────────────

    @Test
    @DisplayName("coordenadora troca quem cuida do paciente")
    void comAuthority_trocarVinculo_passa() {
        chamadorPodeVincular(true);

        assertThatCode(
                        () -> service.update(PACIENTE, 1, "Paciente", null, List.of(CUIDADORA_B)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a authority de vínculo não passa por cima do guarda de cobertura")
    void comAuthority_esvaziarLista_aindaRecusado() {
        // Poder vincular nao e poder deixar o paciente descoberto — sao invariantes diferentes, e
        // a de cobertura (D-204 / ADR-010) nao tem excecao por permissao.
        chamadorPodeVincular(true);

        assertThatThrownBy(() -> service.update(PACIENTE, 1, "Paciente", null, List.of()))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("pac.paciente.sem-cobertura"));
    }
}
