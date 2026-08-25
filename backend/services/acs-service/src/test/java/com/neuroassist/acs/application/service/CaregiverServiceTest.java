package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.api.dto.CaregiverRequest;
import com.neuroassist.acs.api.dto.CaregiverResponse;
import com.neuroassist.acs.application.port.out.CaregiverPatientLinkRepositoryPort;
import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.Cuidador;
import com.neuroassist.acs.domain.model.CuidadorPapel;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import com.neuroassist.acs.domain.model.Tenant;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.domain.model.TenantTipo;
import com.neuroassist.acs.infrastructure.security.TenantContextHolder;
import com.neuroassist.acs.shared.error.CaregiverNotFoundException;
import com.neuroassist.acs.shared.error.TenantAlreadySeededException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.neuroassist.acs.shared.error.LoginDuplicateException;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for CaregiverService (ACS-009, P3). Validates CRUD use-cases: create (with BCrypt),
 * findById, listByTenant, updateStatus.
 */
@ExtendWith(MockitoExtension.class)
class CaregiverServiceTest {

    @Mock private CuidadorRepositoryPort cuidadorRepository;
    @Mock private TenantRepositoryPort tenantRepository;
    @Mock private CaregiverPatientLinkRepositoryPort linkRepository;

    private PasswordEncoder passwordEncoder;
    private CaregiverService caregiverService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID cuidadorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // strength 4 for fast tests
        caregiverService =
                new CaregiverService(
                        cuidadorRepository, tenantRepository, linkRepository, passwordEncoder);
        // RULE-07: populate TenantContextHolder as TenantContextFilter would in real requests
        TenantContextHolder.set(tenantId.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // -----------------------------------------------------------------------
    // create
    // -----------------------------------------------------------------------

    @Test
    void create_withValidRequest_returnsCaregiverResponseWithHashedPassword() {
        CaregiverRequest request =
                new CaregiverRequest("Maria Silva", "maria.silva", "CUIDADORA", "senhaSegura123");

        Tenant activeTenant =
                new Tenant(
                        tenantId,
                        "Clínica Teste",
                        TenantTipo.CLINICA,
                        TenantStatus.ACTIVE,
                        "t_abc",
                        Instant.now());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant));
        when(cuidadorRepository.findByLogin("maria.silva")).thenReturn(Optional.empty());

        Cuidador saved =
                new Cuidador(
                        cuidadorId,
                        tenantId,
                        "Maria Silva",
                        "maria.silva",
                        "$2a$04$hashedpwd",
                        CuidadorPapel.CUIDADORA,
                        CuidadorStatus.ACTIVE,
                        Instant.now(),
                        null);

        when(cuidadorRepository.save(any())).thenReturn(saved);

        CaregiverResponse response = caregiverService.create(request, tenantId);

        assertThat(response.id()).isEqualTo(cuidadorId);
        assertThat(response.tenant()).isEqualTo(tenantId);
        assertThat(response.nome()).isEqualTo("Maria Silva");
        assertThat(response.login()).isEqualTo("maria.silva");
        assertThat(response.papel()).isEqualTo("CUIDADORA");
        assertThat(response.status()).isEqualTo("ACTIVE");
        // SECURITY: credentialHash must not appear in response
    }

    @Test
    void create_hashesPasswordBeforePersisting() {
        CaregiverRequest request =
                new CaregiverRequest("Ana Costa", "ana.costa", "COORDENADOR_CLINICA", "minhasenha");

        Tenant activeTenant =
                new Tenant(
                        tenantId,
                        "Clínica Teste",
                        TenantTipo.CLINICA,
                        TenantStatus.ACTIVE,
                        "t_abc",
                        Instant.now());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant));
        when(cuidadorRepository.findByLogin("ana.costa")).thenReturn(Optional.empty());

        when(cuidadorRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            Cuidador arg = inv.getArgument(0);
                            // Verify BCrypt hash was applied (not plain text)
                            assertThat(arg.credentialHash()).startsWith("$2a$");
                            assertThat(arg.credentialHash()).isNotEqualTo("minhasenha");
                            return new Cuidador(
                                    UUID.randomUUID(),
                                    tenantId,
                                    arg.nome(),
                                    arg.login(),
                                    arg.credentialHash(),
                                    arg.papel(),
                                    arg.status(),
                                    Instant.now(),
                                    null);
                        });

        caregiverService.create(request, tenantId);

        verify(cuidadorRepository).save(any());
    }

    @Test
    void create_lowercasesLogin() {
        CaregiverRequest request =
                new CaregiverRequest("João Souza", "Joao.Souza", "PACIENTE_APOIO", "senha123");

        Tenant activeTenant =
                new Tenant(
                        tenantId,
                        "Clínica Teste",
                        TenantTipo.CLINICA,
                        TenantStatus.ACTIVE,
                        "t_abc",
                        Instant.now());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant));
        when(cuidadorRepository.findByLogin("joao.souza")).thenReturn(Optional.empty());

        when(cuidadorRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            Cuidador arg = inv.getArgument(0);
                            assertThat(arg.login()).isEqualTo("joao.souza");
                            return new Cuidador(
                                    UUID.randomUUID(),
                                    tenantId,
                                    arg.nome(),
                                    arg.login(),
                                    arg.credentialHash(),
                                    arg.papel(),
                                    arg.status(),
                                    Instant.now(),
                                    null);
                        });

        caregiverService.create(request, tenantId);
    }

    // -----------------------------------------------------------------------
    // findById
    // -----------------------------------------------------------------------

    @Test
    void findById_existingId_returnsCaregiverResponse() {
        Cuidador cuidador =
                new Cuidador(
                        cuidadorId,
                        tenantId,
                        "Maria Silva",
                        "maria.silva",
                        "$2a$hash",
                        CuidadorPapel.CUIDADORA,
                        CuidadorStatus.ACTIVE,
                        Instant.now(),
                        null);
        when(cuidadorRepository.findById(cuidadorId)).thenReturn(Optional.of(cuidador));

        CaregiverResponse response = caregiverService.findById(cuidadorId);

        assertThat(response.id()).isEqualTo(cuidadorId);
        assertThat(response.nome()).isEqualTo("Maria Silva");
    }

    @Test
    void findById_nonExistentId_throwsCaregiverNotFoundException() {
        when(cuidadorRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caregiverService.findById(cuidadorId))
                .isInstanceOf(CaregiverNotFoundException.class)
                .hasMessageContaining(cuidadorId.toString());
    }

    // -----------------------------------------------------------------------
    // listByTenant
    // -----------------------------------------------------------------------

    @Test
    void listByTenant_returnsMappedResponses() {
        List<Cuidador> cuidadores =
                List.of(
                        new Cuidador(
                                UUID.randomUUID(),
                                tenantId,
                                "C1",
                                "c1",
                                "$hash",
                                CuidadorPapel.CUIDADORA,
                                CuidadorStatus.ACTIVE,
                                Instant.now(),
                                null),
                        new Cuidador(
                                UUID.randomUUID(),
                                tenantId,
                                "C2",
                                "c2",
                                "$hash",
                                CuidadorPapel.COORDENADOR_CLINICA,
                                CuidadorStatus.ACTIVE,
                                Instant.now(),
                                null));
        when(cuidadorRepository.findByTenantId(tenantId)).thenReturn(cuidadores);

        List<CaregiverResponse> responses = caregiverService.listByTenant(tenantId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).nome()).isEqualTo("C1");
        assertThat(responses.get(1).nome()).isEqualTo("C2");
    }

    @Test
    void listByTenant_emptyTenant_returnsEmptyList() {
        when(cuidadorRepository.findByTenantId(tenantId)).thenReturn(List.of());

        List<CaregiverResponse> responses = caregiverService.listByTenant(tenantId);

        assertThat(responses).isEmpty();
    }

    // -----------------------------------------------------------------------
    // updateStatus
    // -----------------------------------------------------------------------

    @Test
    void updateStatus_existingCaregiver_returnsUpdatedResponse() {
        Cuidador existing =
                new Cuidador(
                        cuidadorId,
                        tenantId,
                        "Maria",
                        "maria",
                        "$hash",
                        CuidadorPapel.CUIDADORA,
                        CuidadorStatus.ACTIVE,
                        Instant.now(),
                        null);

        when(cuidadorRepository.findById(cuidadorId)).thenReturn(Optional.of(existing));
        // No active links — D-204 check passes
        when(linkRepository.findActiveByCuidadorId(cuidadorId)).thenReturn(List.of());
        when(cuidadorRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            Cuidador updated = inv.getArgument(0);
                            assertThat(updated.status()).isEqualTo(CuidadorStatus.INACTIVE);
                            return updated;
                        });

        CaregiverResponse response =
                caregiverService.updateStatus(cuidadorId, CuidadorStatus.INACTIVE);

        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void updateStatus_nonExistentId_throwsCaregiverNotFoundException() {
        when(cuidadorRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caregiverService.updateStatus(cuidadorId, CuidadorStatus.INACTIVE))
                .isInstanceOf(CaregiverNotFoundException.class);
    }

    // ------------------------------------------------------------------------
    // E2E-029 — bootstrap do primeiro coordenador
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("semeia o primeiro coordenador sem exigir tenant do JWT")
    void seedFirstCoordinator_worksWithoutMatchingJwtTenant() {
        UUID novoTenant = UUID.randomUUID();
        when(tenantRepository.findById(novoTenant))
                .thenReturn(
                        Optional.of(
                                new Tenant(
                                        novoTenant,
                                        "Clinica Nova",
                                        TenantTipo.CLINICA,
                                        TenantStatus.PROVISIONING,
                                        null,
                                        Instant.now())));
        when(cuidadorRepository.findByTenantId(novoTenant)).thenReturn(List.of());
        when(cuidadorRepository.findByLogin("coord@nova.dev")).thenReturn(Optional.empty());
        when(cuidadorRepository.save(any())).thenAnswer(inv -> ((Cuidador) inv.getArgument(0)));

        // O TenantContextHolder aponta para OUTRO tenant — e é justamente isso que o
        // `create` normal rejeitaria. Aqui não há de quem exigir contexto: a empresa
        // acabou de nascer e ninguém está dentro dela.
        CaregiverResponse response =
                caregiverService.seedFirstCoordinator(
                        novoTenant, "Coord Nova", "coord@nova.dev", "Test@1234!");

        assertThat(response.papel()).isEqualTo(CuidadorPapel.COORDENADOR_CLINICA.name());
    }

    @Test
    @DisplayName("recusa semear duas vezes — o bootstrap roda uma vez por empresa, e só")
    void seedFirstCoordinator_refusesWhenTenantAlreadyHasCaregivers() {
        UUID novoTenant = UUID.randomUUID();
        when(tenantRepository.findById(novoTenant))
                .thenReturn(
                        Optional.of(
                                new Tenant(
                                        novoTenant,
                                        "Clinica Nova",
                                        TenantTipo.CLINICA,
                                        TenantStatus.ACTIVE,
                                        "t_abc",
                                        Instant.now())));
        when(cuidadorRepository.findByTenantId(novoTenant))
                .thenReturn(
                        List.of(
                                new Cuidador(
                                        UUID.randomUUID(),
                                        novoTenant,
                                        "Ja Existe",
                                        "existente@nova.dev",
                                        "hash",
                                        CuidadorPapel.COORDENADOR_CLINICA,
                                        CuidadorStatus.ACTIVE,
                                        Instant.now(),
                                        null)));

        // Esta é a invariante que substitui a comparação de tenant do JWT. Sem ela, o caminho
        // de bootstrap viraria uma porta permanente para criar gente dentro de outra empresa.
        assertThatThrownBy(
                        () ->
                                caregiverService.seedFirstCoordinator(
                                        novoTenant, "Outro", "outro@nova.dev", "Test@1234!"))
                .isInstanceOf(TenantAlreadySeededException.class);

        verify(cuidadorRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // edit — corrigir nome e login
    //
    // Existe porque erro de digitacao e inevitavel, e ate aqui a unica saida era
    // desativar a pessoa e criar outra. Isso quebra o historico: a confirmacao de
    // alarme guarda o id da cuidadora, e "quem atendeu?" passaria a apontar para
    // uma conta desativada.
    // -----------------------------------------------------------------------

    private Cuidador cuidadoraExistente() {
        return new Cuidador(
                cuidadorId,
                tenantId,
                "Maria Silva",
                "maria.silva",
                "$2a$04$hashedpwd",
                CuidadorPapel.CUIDADORA,
                CuidadorStatus.ACTIVE,
                Instant.now(),
                null);
    }

    @Test
    void edit_corrigeNomeELogin() {
        when(cuidadorRepository.findById(cuidadorId)).thenReturn(Optional.of(cuidadoraExistente()));
        when(cuidadorRepository.findByLogin("maria.souza")).thenReturn(Optional.empty());
        when(cuidadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CaregiverResponse resposta = caregiverService.edit(cuidadorId, "Maria Souza", "maria.souza");

        assertThat(resposta.nome()).isEqualTo("Maria Souza");
        assertThat(resposta.login()).isEqualTo("maria.souza");
    }

    @Test
    void edit_naoTocaEmCredencialPapelNemStatus() {
        // Trocar senha por este caminho daria a quem administra o poder de assumir a
        // conta de outra pessoa; papel muda o que ela pode fazer no sistema inteiro;
        // e status tem endpoint proprio, que carrega a invariante D-204. Um segundo
        // caminho para a mesma escrita seria um caminho SEM a guarda.
        Cuidador existente = cuidadoraExistente();
        when(cuidadorRepository.findById(cuidadorId)).thenReturn(Optional.of(existente));
        when(cuidadorRepository.findByLogin(any())).thenReturn(Optional.empty());
        ArgumentCaptor<Cuidador> capturado = ArgumentCaptor.forClass(Cuidador.class);
        when(cuidadorRepository.save(capturado.capture())).thenAnswer(inv -> inv.getArgument(0));

        caregiverService.edit(cuidadorId, "Nome Novo", "login.novo");

        Cuidador salvo = capturado.getValue();
        assertThat(salvo.credentialHash()).isEqualTo(existente.credentialHash());
        assertThat(salvo.papel()).isEqualTo(existente.papel());
        assertThat(salvo.status()).isEqualTo(existente.status());
    }

    @Test
    void edit_normalizaOLoginParaMinusculas() {
        // Login e identidade de acesso: "Ana@X" e "ana@x" nao podem ser duas pessoas.
        when(cuidadorRepository.findById(cuidadorId)).thenReturn(Optional.of(cuidadoraExistente()));
        when(cuidadorRepository.findByLogin("maria.souza")).thenReturn(Optional.empty());
        ArgumentCaptor<Cuidador> capturado = ArgumentCaptor.forClass(Cuidador.class);
        when(cuidadorRepository.save(capturado.capture())).thenAnswer(inv -> inv.getArgument(0));

        caregiverService.edit(cuidadorId, "Maria", "Maria.Souza");

        assertThat(capturado.getValue().login()).isEqualTo("maria.souza");
    }

    @Test
    void edit_recusaLoginJaUsadoPorOutraPessoa() {
        when(cuidadorRepository.findById(cuidadorId)).thenReturn(Optional.of(cuidadoraExistente()));
        when(cuidadorRepository.findByLogin("ja.existe"))
                .thenReturn(
                        Optional.of(
                                new Cuidador(
                                        UUID.randomUUID(),
                                        tenantId,
                                        "Outra Pessoa",
                                        "ja.existe",
                                        "$2a$04$x",
                                        CuidadorPapel.CUIDADORA,
                                        CuidadorStatus.ACTIVE,
                                        Instant.now(),
                                        null)));

        assertThatThrownBy(() -> caregiverService.edit(cuidadorId, "Maria", "ja.existe"))
                .isInstanceOf(LoginDuplicateException.class);

        verify(cuidadorRepository, never()).save(any());
    }

    @Test
    void edit_reenviarOMesmoLoginNaoEConflitoConsigoMesma() {
        // O caso comum de quem corrige APENAS o nome e devolve o objeto inteiro.
        // Recusar aqui tornaria a edicao de nome impossivel na pratica.
        when(cuidadorRepository.findById(cuidadorId)).thenReturn(Optional.of(cuidadoraExistente()));
        when(cuidadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CaregiverResponse resposta = caregiverService.edit(cuidadorId, "Maria S.", "maria.silva");

        assertThat(resposta.nome()).isEqualTo("Maria S.");
        verify(cuidadorRepository, never()).findByLogin(any());
    }

    @Test
    void edit_cuidadoraInexistenteEstoura() {
        when(cuidadorRepository.findById(cuidadorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caregiverService.edit(cuidadorId, "X", "x"))
                .isInstanceOf(CaregiverNotFoundException.class);
    }
}
