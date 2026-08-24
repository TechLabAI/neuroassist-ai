package com.neuroassist.pac.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.pac.application.port.out.PacienteCuidadorRepositoryPort;
import com.neuroassist.pac.domain.model.PacienteCuidador;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Substituir o conjunto de cuidadoras de um paciente é apagar-tudo-e-inserir, **na mesma
 * transação** — e era exatamente aí que quebrava.
 *
 * <p>O `DELETE` em massa via {@code @Modifying} ia ao banco na hora, mas passava ao lado do
 * contexto de persistência. As entidades carregadas seguiam gerenciadas, o {@code saveAll}
 * seguinte virava {@code merge()} (a entidade usa {@code @EmbeddedId} e não implementa {@code
 * Persistable}, então o Spring Data decide pelo id), o merge encontrava a instância obsoleta ainda
 * gerenciada e **não emitia INSERT nenhum**.
 *
 * <p>Efeito observado no stack real: um `PUT` com uma cuidadora na lista respondia 200 ecoando essa
 * cuidadora, e deixava a tabela **vazia**. O paciente ficava sem cobertura (D-204) sem que o guarda
 * disparasse — porque a lista *pedida* não era vazia, só o resultado.
 *
 * <p>Roda contra Postgres real de propósito: com {@code ddl-auto=create-drop} e um contexto de
 * persistência diferente, o defeito não aparece.
 */
class PacienteCuidadorReplaceIT extends BaseIntegrationTest {

    private static final String TENANT = "t_emp_test";

    @Autowired private PacienteCuidadorRepositoryPort cuidadorRepository;
    @Autowired private com.neuroassist.pac.application.port.out.PacienteRepositoryPort pacienteRepository;

    private UUID paciente;
    private final UUID cuidadoraA = UUID.randomUUID();
    private final UUID cuidadoraB = UUID.randomUUID();

    /** A FK exige que o paciente exista antes do vinculo. */
    private void criarPaciente() {
        Instant agora = Instant.now();
        paciente =
                pacienteRepository
                        .save(
                                new com.neuroassist.pac.domain.model.Paciente(
                                        null, TENANT, "Paciente do IT", null, List.of(), 0, 0,
                                        agora, agora, null))
                        .id();
    }

    /**
     * {@code @BeforeTransaction}, e nao {@code @BeforeEach}.
     *
     * <p>O Spring abre a transacao do teste em {@code beforeTestMethod}, que roda ANTES do
     * {@code @BeforeEach} do JUnit — e o Hibernate fixa o identificador de tenant da Session no
     * instante em que ela nasce. Ligando o tenant depois, a Session ja nasceu apontando para o
     * fallback {@code neuroassist_global}, onde {@code paciente_cuidador} nao existe.
     *
     * <p>E a mesma armadilha que CLAUDE.md descreve para producao ("nunca resolva o tenant dentro
     * de um metodo @Transactional"), vista do lado do teste. Ela me pegou ao escrever este arquivo.
     */
    @BeforeTransaction
    void bindTenant() {
        TenantContextHolder.set(TENANT);
    }

    @AfterTransaction
    void clearTenant() {
        TenantContextHolder.clear();
    }

    /**
     * O caso que quebrava: apagar e reinserir dentro de UMA transação.
     *
     * <p>{@code Propagation.REQUIRES_NEW} não serve aqui — o ponto é justamente que as duas
     * operações compartilhem o contexto de persistência, como acontece em {@code
     * PacienteService.update}.
     */
    @Test
    @Transactional(propagation = Propagation.REQUIRED)
    @DisplayName("substituir o conjunto na mesma transação persiste os novos vínculos")
    void substituirConjunto_persisteOsNovos() {
        criarPaciente();
        cuidadorRepository.saveAll(
                List.of(
                        new PacienteCuidador(paciente, cuidadoraA, Instant.now()),
                        new PacienteCuidador(paciente, cuidadoraB, Instant.now())));

        // Le antes de destruir — e este read e o que deixava as entidades gerenciadas, armando a
        // armadilha. PacienteService.update faz exatamente isto para calcular o delta do outbox.
        List<PacienteCuidador> antes = cuidadorRepository.findByPacienteId(paciente);
        assertThat(antes).hasSize(2);

        cuidadorRepository.deleteByPacienteId(paciente);
        cuidadorRepository.saveAll(
                List.of(new PacienteCuidador(paciente, cuidadoraA, Instant.now())));

        List<PacienteCuidador> depois = cuidadorRepository.findByPacienteId(paciente);

        assertThat(depois)
                .as(
                        "o vinculo remanescente tem de existir — sem clearAutomatically no DELETE em"
                            + " massa, o saveAll virava merge de uma entidade obsoleta ainda"
                            + " gerenciada e NENHUM insert era emitido")
                .hasSize(1);
        assertThat(depois.get(0).cuidadorResponsavelId()).isEqualTo(cuidadoraA);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRED)
    @DisplayName("substituir por um conjunto totalmente novo também persiste")
    void substituirPorOutroConjunto_persiste() {
        criarPaciente();
        cuidadorRepository.saveAll(
                List.of(new PacienteCuidador(paciente, cuidadoraA, Instant.now())));
        cuidadorRepository.findByPacienteId(paciente);

        cuidadorRepository.deleteByPacienteId(paciente);
        cuidadorRepository.saveAll(
                List.of(new PacienteCuidador(paciente, cuidadoraB, Instant.now())));

        assertThat(cuidadorRepository.findByPacienteId(paciente))
                .extracting(PacienteCuidador::cuidadorResponsavelId)
                .containsExactly(cuidadoraB);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRED)
    @DisplayName("esvaziar o conjunto realmente esvazia")
    void esvaziarConjunto_esvazia() {
        criarPaciente();
        cuidadorRepository.saveAll(
                List.of(new PacienteCuidador(paciente, cuidadoraA, Instant.now())));
        cuidadorRepository.findByPacienteId(paciente);

        cuidadorRepository.deleteByPacienteId(paciente);

        assertThat(cuidadorRepository.findByPacienteId(paciente)).isEmpty();
    }
}
