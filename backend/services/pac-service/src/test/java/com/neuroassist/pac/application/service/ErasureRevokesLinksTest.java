package com.neuroassist.pac.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.pac.application.port.out.AcsDirectoryPort;
import com.neuroassist.pac.application.port.out.AcsLinkPort;
import com.neuroassist.pac.application.port.out.CallerAuthoritiesPort;
import com.neuroassist.pac.application.port.out.DirectoryOutboxPort;
import com.neuroassist.pac.application.port.out.EdgeDevicePort;
import com.neuroassist.pac.application.port.out.HistErasurePort;
import com.neuroassist.pac.application.port.out.PacienteCuidadorRepositoryPort;
import com.neuroassist.pac.application.port.out.PacienteRepositoryPort;
import com.neuroassist.pac.domain.model.Paciente;
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
import org.mockito.InOrder;

/**
 * Apagar os dados de um paciente (LGPD) tem de revogar o acesso a ele.
 *
 * <p>Antes disto, o apagamento removia o dado pessoal e deixava o vínculo <b>ATIVO</b> no ACS: a
 * associação "esta cuidadora cuida desta pessoa" sobrevivia ao apagamento da pessoa. E associação é
 * dado. No ambiente de desenvolvimento havia 7 casos assim.
 */
class ErasureRevokesLinksTest {

    private static final String TENANT = "e2e00000-0000-0000-0000-000000000001";
    private static final UUID PACIENTE = UUID.randomUUID();

    private PacienteRepositoryPort pacienteRepository;
    private PacienteCuidadorRepositoryPort cuidadorRepository;
    private HistErasurePort histErasurePort;
    private AcsLinkPort acsLinkPort;
    private PacienteService service;

    @BeforeEach
    void setUp() {
        pacienteRepository = mock(PacienteRepositoryPort.class);
        cuidadorRepository = mock(PacienteCuidadorRepositoryPort.class);
        histErasurePort = mock(HistErasurePort.class);
        acsLinkPort = mock(AcsLinkPort.class);
        service =
                new PacienteService(
                        pacienteRepository,
                        cuidadorRepository,
                        mock(DirectoryOutboxPort.class),
                        new PacienteDomainService(),
                        mock(EdgeDevicePort.class),
                        mock(AcsDirectoryPort.class),
                        histErasurePort,
                        mock(CallerAuthoritiesPort.class),
                        acsLinkPort);
        TenantContextHolder.set(TENANT);

        Paciente vivo =
                new Paciente(
                        PACIENTE,
                        TENANT,
                        "Márcio Amaral",
                        null,
                        List.of(),
                        0,
                        0,
                        Instant.now(),
                        Instant.now(),
                        null);
        when(pacienteRepository.findByIdAndTenantId(PACIENTE, TENANT))
                .thenReturn(Optional.of(vivo));
        when(pacienteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("apagar o paciente revoga os vínculos no ACS e limpa a projeção local")
    void apagar_revogaVinculos() {
        when(acsLinkPort.revokeAllForErasedPatient(PACIENTE))
                .thenReturn(AcsLinkPort.Outcome.ACCEPTED);

        service.erasePersonalData(PACIENTE);

        verify(acsLinkPort).revokeAllForErasedPatient(PACIENTE);
        verify(cuidadorRepository).deleteByPacienteId(PACIENTE);
    }

    @Test
    @DisplayName("a ordem é HIST, depois ACS, depois anonimizar aqui")
    void ordem_externosAntesDoLocal() {
        // A ordem e a propriedade que mantem a operacao retentavel: os sistemas externos sao
        // pedidos enquanto o dado local ainda existe, porque e ele que uma retentativa precisaria.
        when(acsLinkPort.revokeAllForErasedPatient(PACIENTE))
                .thenReturn(AcsLinkPort.Outcome.ACCEPTED);

        service.erasePersonalData(PACIENTE);

        InOrder ordem = inOrder(histErasurePort, acsLinkPort, pacienteRepository);
        ordem.verify(histErasurePort).erasePatientData(PACIENTE, TENANT);
        ordem.verify(acsLinkPort).revokeAllForErasedPatient(PACIENTE);
        ordem.verify(pacienteRepository).save(any());
    }

    @Test
    @DisplayName("ACS indisponível aborta o apagamento — nada local é anonimizado")
    void acsIndisponivel_abortaSemAnonimizar() {
        when(acsLinkPort.revokeAllForErasedPatient(PACIENTE)).thenReturn(AcsLinkPort.Outcome.RETRY);

        assertThatThrownBy(() -> service.erasePersonalData(PACIENTE))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("pac.paciente.erasure-vinculo-falhou"));

        // O ponto: a requisicao segue retentavel porque o nome ainda esta la. Anonimizar primeiro
        // e falhar depois deixaria o dado local destruido e o acesso vivo — o pior dos dois.
        verify(pacienteRepository, never()).save(any());
        verify(cuidadorRepository, never()).deleteByPacienteId(any());
    }

    @Test
    @DisplayName("recusa terminal do ACS também aborta — não se apaga pela metade")
    void acsRecusa_abortaTambem() {
        when(acsLinkPort.revokeAllForErasedPatient(PACIENTE))
                .thenReturn(AcsLinkPort.Outcome.REFUSED);

        assertThatThrownBy(() -> service.erasePersonalData(PACIENTE))
                .isInstanceOf(CodedException.class);

        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("apagar duas vezes não chama o ACS de novo — idempotente")
    void apagarDuasVezes_naoRepeteOEfeito() {
        Paciente jaApagado =
                new Paciente(
                        PACIENTE,
                        TENANT,
                        Paciente.ERASED_NOME,
                        null,
                        List.of(),
                        1,
                        0,
                        Instant.now(),
                        Instant.now(),
                        Instant.now());
        when(pacienteRepository.findByIdAndTenantId(PACIENTE, TENANT))
                .thenReturn(Optional.of(jaApagado));

        assertThatCode(() -> service.erasePersonalData(PACIENTE)).doesNotThrowAnyException();

        verify(acsLinkPort, never()).revokeAllForErasedPatient(any());
        verify(histErasurePort, never()).erasePatientData(any(), any());
    }
}
