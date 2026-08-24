package com.neuroassist.pac.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.pac.application.port.out.AcsDirectoryPort;
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
 * Guarda de cobertura D-204 no PAC (E2E-028 / ADR-010).
 *
 * <p>Antes da E2E-015 este conflito era invisível porque os dois lados nunca se falavam: o PAC
 * aceitava esvaziar a lista com 200 e o ACS recusava com 409 depois, deixando os dois bancos
 * discordando e uma entrada de outbox em {@code failed}. Estes testes existem para que o guarda
 * não volte a sumir.
 */
class CoberturaGuardTest {

    private static final String TENANT = "e2e00000-0000-0000-0000-000000000001";
    private static final UUID PACIENTE = UUID.randomUUID();
    private static final UUID CUIDADORA_A = UUID.randomUUID();
    private static final UUID CUIDADORA_B = UUID.randomUUID();

    private PacienteRepositoryPort pacienteRepository;
    private PacienteCuidadorRepositoryPort cuidadorRepository;
    private PacienteService service;

    @BeforeEach
    void setUp() {
        pacienteRepository = org.mockito.Mockito.mock(PacienteRepositoryPort.class);
        cuidadorRepository = org.mockito.Mockito.mock(PacienteCuidadorRepositoryPort.class);
        service =
                new PacienteService(
                        pacienteRepository,
                        cuidadorRepository,
                        org.mockito.Mockito.mock(DirectoryOutboxPort.class),
                        new PacienteDomainService(),
                        org.mockito.Mockito.mock(EdgeDevicePort.class),
                        org.mockito.Mockito.mock(AcsDirectoryPort.class),
                        org.mockito.Mockito.mock(HistErasurePort.class),
                        // Coordenadora: pode vincular. O guarda de cobertura e outra
                        // invariante e nao tem excecao por permissao.
                        a -> true);
        TenantContextHolder.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static PacienteCuidador vinculo(UUID cuidadoraId) {
        return new PacienteCuidador(PACIENTE, cuidadoraId, Instant.now());
    }

    private void pacienteComCuidadoras(UUID... cuidadoras) {
        when(cuidadorRepository.findByPacienteId(PACIENTE))
                .thenReturn(java.util.Arrays.stream(cuidadoras).map(CoberturaGuardTest::vinculo).toList());
    }

    private void pacienteExiste() {
        Paciente p =
                new Paciente(
                        PACIENTE, TENANT, "Paciente", null, List.of(), 0, 0,
                        Instant.now(), Instant.now(), null);
        when(pacienteRepository.findByIdAndTenantId(PACIENTE, TENANT)).thenReturn(Optional.of(p));
        when(pacienteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── o defeito que a issue descreve ───────────────────────────────────────

    @Test
    @DisplayName("esvaziar a lista da última cuidadora é recusado — 422, não 200 seguido de 409")
    void removerAUltimaCuidadora_recusado() {
        pacienteComCuidadoras(CUIDADORA_A);

        assertThatThrownBy(() -> service.update(PACIENTE, 1, "Paciente", null, List.of()))
                .isInstanceOf(CodedException.class)
                .satisfies(
                        ex ->
                                assertThat(((CodedException) ex).code())
                                        .isEqualTo("pac.paciente.sem-cobertura"));

        // O ponto: nada e' escrito. Antes, o PAC gravava e so depois o ACS discordava.
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("a mensagem diz o que fazer — adicionar antes de remover")
    void mensagem_ensinaOCaminho() {
        pacienteComCuidadoras(CUIDADORA_A);

        assertThatThrownBy(() -> service.update(PACIENTE, 1, "Paciente", null, List.of()))
                .hasMessageContaining("Add another caregiver before removing the last one");
    }

    // ── o que NÃO pode ser bloqueado ─────────────────────────────────────────

    @Test
    @DisplayName("remover UMA de duas cuidadoras passa — a invariante é sobre ficar em zero")
    void removerUmaDeDuas_passa() {
        pacienteExiste();
        pacienteComCuidadoras(CUIDADORA_A, CUIDADORA_B);

        assertThatCode(() -> service.update(PACIENTE, 1, "Paciente", null, List.of(CUIDADORA_A)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("paciente que JÁ estava sem cuidadora continua editável")
    void jaEstavaDescoberto_continuaEditavel() {
        // Bloquear aqui nao restauraria cobertura nenhuma — so prenderia o paciente num estado do
        // qual ele nao consegue sair pela unica porta que tem.
        pacienteExiste();
        pacienteComCuidadoras();

        assertThatCode(() -> service.update(PACIENTE, 1, "Outro nome", null, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("trocar a única cuidadora por outra passa — não é remoção, é substituição")
    void substituirAUnicaCuidadora_passa() {
        pacienteExiste();
        pacienteComCuidadoras(CUIDADORA_A);

        assertThatCode(() -> service.update(PACIENTE, 1, "Paciente", null, List.of(CUIDADORA_B)))
                .doesNotThrowAnyException();
    }
}
