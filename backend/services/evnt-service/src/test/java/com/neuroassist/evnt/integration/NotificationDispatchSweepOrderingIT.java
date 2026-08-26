package com.neuroassist.evnt.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.evnt.application.port.out.NotificationDispatchRepositoryPort;
import com.neuroassist.evnt.domain.model.DispatchStatuses;
import com.neuroassist.evnt.domain.model.EscalationGrade;
import com.neuroassist.evnt.domain.model.Feedback;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.domain.model.Tier;
import com.neuroassist.evnt.infrastructure.persistence.entity.EventoJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.entity.NotificationDispatchJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.EventoJpaRepository;
import com.neuroassist.evnt.infrastructure.persistence.repository.NotificationDispatchJpaRepository;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A justiça do sweep, contra PostgreSQL real — E2E-020 / ADR-011 §2.
 *
 * <p>O ADR-011 aceita que a fila passe a conter alarmes antigos: eles não são mais descartados. A
 * consequência que precisa ser guardada é a outra ponta disso — com o batch menor que o backlog, um
 * alarme antigo que já deu voltas na escada não pode consumir a varredura inteira e deixar sem
 * atendimento o alarme que está acontecendo agora.
 *
 * <p>Roda contra o banco de verdade porque a ordem e o filtro moram numa {@code @Query} JPQL. Um
 * teste com repositório de mentira validaria a mentira: é exatamente a armadilha que este projeto
 * já pagou 18 vezes (plan-e2e-integration §11).
 */
@DisplayName("Ordem e elegibilidade do sweep de dispatches")
class NotificationDispatchSweepOrderingIT extends BaseIntegrationTest {

    private static final String TENANT = "t_emp_test";

    @Autowired private NotificationDispatchRepositoryPort port;
    @Autowired private NotificationDispatchJpaRepository jpaRepository;
    @Autowired private EventoJpaRepository eventoJpaRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TENANT);
        // Ordem importa: notification_dispatch tem FK para evento (V0503) — apagar o pai primeiro
        // quebraria a limpeza se a FK não fosse ON DELETE CASCADE; aqui zeramos os dois de propósito.
        transactionTemplate.executeWithoutResult(s -> jpaRepository.deleteAll());
        transactionTemplate.executeWithoutResult(s -> eventoJpaRepository.deleteAll());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName(
            "Com o batch cheio, o alarme recém-chegado passa à frente do que já rodou a escada")
    void alarmeNovoNaoEsperaOAntigo() {
        Instant agora = Instant.now();
        UUID antigo =
                inserir(
                        DispatchStatuses.NO_SESSION,
                        /* round= */ 3,
                        /* lastAttemptAt= */ agora.minus(Duration.ofHours(6)),
                        /* nextAttemptAt= */ null);
        // O alarme novo JÁ foi tentado uma vez, no ingress — é o caso real. Por data de última
        // tentativa ele perderia para o antigo; é a rodada da escada que o coloca na frente.
        UUID novo =
                inserir(
                        DispatchStatuses.NO_SESSION,
                        /* round= */ 0,
                        /* lastAttemptAt= */ agora.minus(Duration.ofMinutes(1)),
                        /* nextAttemptAt= */ null);

        List<NotificationDispatch> umSo = port.findDueForRetry(agora, 1);

        assertThat(umSo).hasSize(1);
        assertThat(umSo.get(0).id())
                .as("o batch de um foi para o alarme novo, não para o antigo")
                .isEqualTo(novo);

        assertThat(port.findDueForRetry(agora, 10))
                .as("e o antigo continua elegível — só não na frente")
                .extracting(NotificationDispatch::id)
                .containsExactly(novo, antigo);
    }

    @Test
    @DisplayName("Uma linha em backoff não é candidata até a espera vencer")
    void backoffTiraDaVarredura() {
        Instant agora = Instant.now();
        UUID emEspera =
                inserir(
                        DispatchStatuses.QUEUED,
                        0,
                        agora.minus(Duration.ofMinutes(1)),
                        agora.plus(Duration.ofMinutes(10)));

        assertThat(port.findDueForRetry(agora, 10)).as("ainda esperando o backoff").isEmpty();
        assertThat(port.findDueForRetry(agora.plus(Duration.ofMinutes(11)), 10))
                .as("vencida a espera, volta a ser candidata — nunca foi descartada")
                .extracting(NotificationDispatch::id)
                .containsExactly(emEspera);
    }

    @Test
    @DisplayName("Estados terminais nunca voltam ao sweep, e ESCALATED some do painel da cuidadora")
    void terminaisNaoRetornam() {
        Instant agora = Instant.now();
        inserir(DispatchStatuses.DELIVERED, 0, agora, null);
        inserir(DispatchStatuses.ACKNOWLEDGED, 0, agora, null);
        UUID escalada = inserir(DispatchStatuses.ESCALATED, 0, agora, null);
        inserir(DispatchStatuses.FAILED, 0, agora, null);

        assertThat(port.findDueForRetry(agora, 10)).isEmpty();
        assertThat(port.findUnacknowledgedFor("cg-a"))
                .as("uma linha já passada adiante não é pendência de ninguém")
                .extracting(NotificationDispatch::id)
                .doesNotContain(escalada);
    }

    @Test
    @DisplayName("As colunas da V0502 sobrevivem ao ida-e-volta pelo banco")
    void colunasDaEscadaPersistem() {
        Instant agora = Instant.now();
        UUID id = UUID.randomUUID();
        UUID eventoId = criarEvento();
        NotificationDispatch original =
                new NotificationDispatch(
                        id,
                        eventoId,
                        Tier.full_screen_alarm.name(),
                        "cg-a",
                        DispatchStatuses.ESCALATED,
                        7,
                        agora,
                        2,
                        EscalationGrade.COORDENADOR_CLINICA,
                        1,
                        agora.plus(Duration.ofMinutes(2)),
                        "janela esgotada apos 7 tentativas para cg-a");
        transactionTemplate.executeWithoutResult(s -> port.save(original));

        NotificationDispatch lida = port.findById(id).orElseThrow();
        assertThat(lida.escalationOrder()).isEqualTo(2);
        assertThat(lida.escalationGrade())
                .as(
                        "o grau normativo sobrevive ao banco — é ele que responde 'por que a"
                                + " coordenadora foi acionada?'")
                .isEqualTo(EscalationGrade.COORDENADOR_CLINICA);
        assertThat(lida.escalationRound()).isEqualTo(1);
        assertThat(lida.nextAttemptAt()).isNotNull();
        assertThat(lida.outcomeReason()).isEqualTo(original.outcomeReason());
    }

    private UUID inserir(String status, int round, Instant lastAttemptAt, Instant nextAttemptAt) {
        UUID id = UUID.randomUUID();
        UUID eventoId = criarEvento();
        transactionTemplate.executeWithoutResult(
                s ->
                        jpaRepository.save(
                                NotificationDispatchJpaEntity.builder()
                                        .id(id)
                                        .eventoId(eventoId)
                                        .tier(Tier.full_screen_alarm.name())
                                        .targetCaregiverId("cg-a")
                                        .status(status)
                                        .attempts(0)
                                        .lastAttemptAt(lastAttemptAt)
                                        .escalationOrder(0)
                                        .escalationRound(round)
                                        .nextAttemptAt(nextAttemptAt)
                                        .build()));
        return id;
    }

    /**
     * notification_dispatch.evento_id tem FK para evento (V0503) — este teste avalia só o sweep de
     * dispatch, então o evento em si é um detalhe irrelevante; cada linha ganha o seu próprio para
     * satisfazer a constraint sem acoplar os testes a um evento compartilhado.
     */
    private UUID criarEvento() {
        UUID id = UUID.randomUUID();
        Instant agora = Instant.now();
        transactionTemplate.executeWithoutResult(
                s ->
                        eventoJpaRepository.save(
                                EventoJpaEntity.builder()
                                        .id(id)
                                        .pacienteId("pac-sweep-test")
                                        .comandoRef("cmd-sweep-test")
                                        .commandLabelSnapshot("Sweep Test Command")
                                        .urgenciaSnapshot("emergencia")
                                        .feedback(Feedback.NAO_AVALIADO.name())
                                        .recognizedAt(agora)
                                        .createdAt(agora)
                                        .tenantId(TENANT)
                                        .build()));
        return id;
    }
}
