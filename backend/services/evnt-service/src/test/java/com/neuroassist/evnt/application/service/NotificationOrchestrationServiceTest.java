package com.neuroassist.evnt.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.application.port.out.NotificationDispatchPort;
import com.neuroassist.evnt.domain.model.DispatchStatuses;
import com.neuroassist.evnt.domain.model.EscalationGrade;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.domain.model.Tier;
import com.neuroassist.evnt.domain.service.AlarmRelevancePolicy;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * O que o ADR-011 decidiu, exercitado — E2E-020.
 *
 * <p>Estes são os testes que alguém vai querer ver depois de um incidente: por que a cuidadora X
 * não foi avisada, por que aquele alarme saiu da fila, e se um pedido de ajuda pode ter sido
 * descartado por decurso de prazo.
 */
@DisplayName("Escalonamento e expiração de alarmes (ADR-011)")
class NotificationOrchestrationServiceTest {

    private static final String TENANT = "t_emp_test";
    private static final String PACIENTE = "pac-1";
    private static final String CG_A = "cg-a";
    private static final String CG_B = "cg-b";

    private InMemoryDispatchRepository dispatchRepository;
    private EventoRepositoryPort eventoRepository;
    private NotificationDispatchPort dispatchPort;
    private CaregiverRoutingPort caregiverRoutingPort;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TENANT);
        dispatchRepository = new InMemoryDispatchRepository();
        eventoRepository = mock(EventoRepositoryPort.class);
        dispatchPort = mock(NotificationDispatchPort.class);
        caregiverRoutingPort = mock(CaregiverRoutingPort.class);

        // O dash-service nunca acha sessão aberta: é o cenário do ADR — a cuidadora não está lá.
        when(dispatchPort.dispatch(any(), any()))
                .thenReturn(
                        new NotificationDispatchPort.DispatchOutcome(
                                NotificationDispatchPort.DispatchOutcome.NO_OPEN_SESSION));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // A regra mais importante do ADR-011
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "Um alarme de ajuda não sai da fila por tempo nenhum — nem com política de expiração"
                    + " configurada para a urgência dele")
    void alarmeDeAjudaNuncaExpira() {
        // Política hostil de propósito: a urgência deste alarme TEM janela configurada, e uma
        // janela absurdamente curta. Se "ajuda nunca expira" for opcional, este teste a derruba.
        var service =
                serviceCom(
                        new AlarmRelevancePolicy(
                                Map.of("rotina", Duration.ofSeconds(1)),
                                Set.of("ajuda", "socorro", "help")),
                        2,
                        0L);

        UUID eventoId = UUID.randomUUID();
        // Nasceu há 30 dias e ninguém atendeu.
        evento(
                eventoId,
                "rotina",
                "cmd-ajuda",
                "Preciso de ajuda",
                Instant.now().minus(Duration.ofDays(30)));
        ladder(CG_A, CG_B);
        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        UUID.randomUUID(),
                        eventoId,
                        Tier.discreet.name(),
                        CG_A,
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        // Rodadas suficientes para esgotar a escada muitas vezes.
        for (int i = 0; i < 60; i++) {
            service.retrySweep();
            assertThat(dispatchRepository.all())
                    .as("nenhuma linha deste alarme pode chegar a FAILED na varredura %d", i)
                    .noneMatch(d -> DispatchStatuses.FAILED.equals(d.status()));
        }

        assertThat(dispatchRepository.findDueForRetry(Instant.now(), 100))
                .as("o alarme de ajuda continua vivo na fila depois de 60 varreduras")
                .isNotEmpty();
        assertThat(dispatchRepository.all())
                .as("passou por mais de uma rodada da escada, e ainda assim não expirou")
                .anyMatch(d -> d.escalationRound() > 0);
    }

    // -------------------------------------------------------------------------
    // A escada (ADR-011 §1)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "A escada aciona a cuidadora seguinte só depois de a anterior falhar, e registra as"
                    + " duas tentativas")
    void escadaSobeUmDegrauPorVezERegistraCadaUm() {
        var service = serviceCom(semExpiracao(), 2, 0L);

        UUID eventoId = UUID.randomUUID();
        evento(eventoId, "emergencia", "cmd-emergencia", "Emergência", Instant.now());
        ladder(CG_A, CG_B);
        UUID degrauZero = UUID.randomUUID();
        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        degrauZero,
                        eventoId,
                        Tier.full_screen_alarm.name(),
                        CG_A,
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        // Duas varreduras: a janela de CG_A ainda não esgotou.
        service.retrySweep();
        service.retrySweep();

        assertThat(alvosTentados())
                .as(
                        "enquanto a janela da destinatária original não esgota, a seguinte não é"
                                + " acionada — é escada, não leque")
                .containsOnly(CG_A);
        assertThat(dispatchRepository.findByEventoId(eventoId))
                .as("nenhuma linha para a segunda cuidadora ainda existe")
                .hasSize(1);

        // Terceira varredura: attempts == 2 == max por degrau → escalona.
        service.retrySweep();

        List<NotificationDispatch> trilha = dispatchRepository.findByEventoId(eventoId);
        assertThat(trilha).as("a trilha da escada tem as duas tentativas registradas").hasSize(2);

        NotificationDispatch anterior =
                trilha.stream().filter(d -> CG_A.equals(d.caregiverId())).findFirst().orElseThrow();
        assertThat(anterior.status()).isEqualTo(DispatchStatuses.ESCALATED);
        assertThat(anterior.attempts()).isEqualTo(2);
        assertThat(anterior.lastAttemptAt()).isNotNull();
        assertThat(anterior.outcomeReason())
                .as("a linha fechada diz quantas tentativas houve e para quem o alarme foi")
                .contains("janela esgotada")
                .contains(CG_B);

        NotificationDispatch seguinte =
                trilha.stream().filter(d -> CG_B.equals(d.caregiverId())).findFirst().orElseThrow();
        assertThat(seguinte.status()).isEqualTo(DispatchStatuses.QUEUED);
        assertThat(seguinte.escalationOrder()).as("degrau 1 da escada").isEqualTo(1);
        assertThat(seguinte.escalationRound()).as("ainda na primeira passada").isZero();
        assertThat(seguinte.tier())
                .as("o tier do alarme original acompanha a escada")
                .isEqualTo(Tier.full_screen_alarm.name());
    }

    @Test
    @DisplayName("Esgotada a escada, o alarme reinicia no degrau 0 numa nova rodada — nunca FAILED")
    void escadaEsgotadaReiniciaEmVezDeDesistir() {
        var service = serviceCom(semExpiracao(), 1, 60_000L);

        UUID eventoId = UUID.randomUUID();
        evento(eventoId, "emergencia", "cmd-emergencia", "Emergência", Instant.now());
        ladder(CG_A, CG_B);
        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        UUID.randomUUID(),
                        eventoId,
                        Tier.full_screen_alarm.name(),
                        CG_A,
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        service.retrySweep(); // tenta CG_A (attempts 0 → 1)
        service.retrySweep(); // janela de CG_A esgotada → degrau 1 (CG_B)
        service.retrySweep(); // tenta CG_B (attempts 0 → 1)
        service.retrySweep(); // janela de CG_B esgotada → escada esgotada → nova rodada

        List<NotificationDispatch> trilha = dispatchRepository.findByEventoId(eventoId);
        assertThat(trilha)
                .as("nenhuma linha deste alarme virou FAILED por esgotamento")
                .noneMatch(d -> DispatchStatuses.FAILED.equals(d.status()));

        NotificationDispatch novaRodada =
                trilha.stream().filter(d -> d.escalationRound() == 1).findFirst().orElseThrow();
        assertThat(novaRodada.caregiverId()).isEqualTo(CG_A);
        assertThat(novaRodada.escalationOrder()).isZero();
        assertThat(novaRodada.status()).isEqualTo(DispatchStatuses.QUEUED);
        assertThat(novaRodada.nextAttemptAt())
                .as(
                        "a nova rodada espera o backoff — sem isso, 'nunca desistir' vira loop"
                                + " apertado e o alarme antigo monopoliza o sweep")
                .isNotNull()
                .isAfter(Instant.now().plus(Duration.ofSeconds(30)));

        NotificationDispatch fechada =
                trilha.stream().filter(d -> CG_B.equals(d.caregiverId())).findFirst().orElseThrow();
        assertThat(fechada.status()).isEqualTo(DispatchStatuses.ESCALATED);
        assertThat(fechada.outcomeReason()).contains("escada esgotada");
    }

    @Test
    @DisplayName(
            "Um alarme antigo em backoff não impede um alarme recém-chegado de ser entregue, mesmo"
                    + " com o batch cheio")
    void alarmeAntigoNaoMonopolizaOSweep() {
        // batch de 1: se o alarme antigo continuasse elegível, ele consumiria a varredura inteira.
        var service = serviceCom(semExpiracao(), 1, 60_000L, 1);

        UUID antigo = UUID.randomUUID();
        evento(
                antigo,
                "emergencia",
                "cmd-emergencia",
                "Emergência antiga",
                Instant.now().minus(Duration.ofHours(6)));
        ladder(CG_A);
        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        UUID.randomUUID(),
                        antigo,
                        Tier.full_screen_alarm.name(),
                        CG_A,
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        service.retrySweep(); // tentativa do antigo
        service.retrySweep(); // escada de um degrau esgota → nova rodada, em backoff

        // Agora chega um alarme novo, e o dash-service passa a entregar.
        when(dispatchPort.dispatch(any(), any()))
                .thenReturn(
                        new NotificationDispatchPort.DispatchOutcome(
                                NotificationDispatchPort.DispatchOutcome.DELIVERED));
        UUID novo = UUID.randomUUID();
        evento(novo, "emergencia", "cmd-emergencia", "Emergência nova", Instant.now());
        UUID linhaNova = UUID.randomUUID();
        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        linhaNova,
                        novo,
                        Tier.full_screen_alarm.name(),
                        CG_A,
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        var resultado = service.retrySweep();

        assertThat(resultado.delivered())
                .as("o alarme que está acontecendo agora foi atendido nesta varredura")
                .isEqualTo(1);
        assertThat(dispatchRepository.findById(linhaNova).orElseThrow().status())
                .isEqualTo(DispatchStatuses.DELIVERED);
        assertThat(dispatchRepository.findByEventoId(antigo))
                .as("e o alarme antigo continua na fila, apenas esperando a vez")
                .anyMatch(d -> DispatchStatuses.isRetryable(d.status()));
    }

    @Test
    @DisplayName(
            "A escada percorre os quatro graus na ordem normativa: cuidadora original → demais"
                    + " cuidadoras → coordenadora da clínica → apoio do paciente")
    void escadaPercorreOsQuatroGrausNaOrdem() {
        var service = serviceCom(semExpiracao(), 1, 0L);

        UUID eventoId = UUID.randomUUID();
        evento(eventoId, "emergencia", "cmd-emergencia", "Emergência", Instant.now());
        // De propósito fora de ordem e com ids que, se ordenados só alfabeticamente, dariam a
        // sequência errada: o apoio ("apoio-a") viria antes de tudo.
        vinculosDoPaciente("apoio-a:PACIENTE_APOIO", "cg-z:CUIDADORA", "cg-b:CUIDADORA");
        coordenadorasDaClinica("coord-x:COORDENADOR_CLINICA");

        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        UUID.randomUUID(),
                        eventoId,
                        Tier.full_screen_alarm.name(),
                        "cg-b",
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        // Cada rodada: uma tentativa (max 1 por degrau) e um escalonamento.
        for (int i = 0; i < 8; i++) {
            service.retrySweep();
        }

        List<NotificationDispatch> trilha = dispatchRepository.findByEventoId(eventoId);
        assertThat(trilha)
                .as("a ordem em que os quatro graus foram acionados é normativa")
                .extracting(NotificationDispatch::caregiverId)
                .startsWith("cg-b", "cg-z", "coord-x", "apoio-a");
        assertThat(trilha)
                .extracting(NotificationDispatch::escalationGrade)
                .startsWith(
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        EscalationGrade.CUIDADORA_ADICIONAL,
                        EscalationGrade.COORDENADOR_CLINICA,
                        EscalationGrade.PACIENTE_APOIO);

        NotificationDispatch coordenadora =
                trilha.stream()
                        .filter(d -> "coord-x".equals(d.caregiverId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(coordenadora.escalationOrder()).as("terceiro degrau").isEqualTo(2);

        NotificationDispatch queFechouParaACoordenadora =
                trilha.stream()
                        .filter(d -> "cg-z".equals(d.caregiverId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(queFechouParaACoordenadora.outcomeReason())
                .as("por que a coordenadora foi acionada, respondido na própria trilha")
                .contains("CUIDADORA_ADICIONAL")
                .contains("COORDENADOR_CLINICA");
    }

    @Test
    @DisplayName(
            "O apoio é o ÚLTIMO grau: não é acionado enquanto houver cuidadora ou coordenadora"
                    + " por tentar")
    void apoioSoDepoisDeTodosOsOutros() {
        var service = serviceCom(semExpiracao(), 1, 0L);

        UUID eventoId = UUID.randomUUID();
        evento(eventoId, "emergencia", "cmd-emergencia", "Emergência", Instant.now());
        vinculosDoPaciente("apoio-a:PACIENTE_APOIO", "cg-b:CUIDADORA");
        coordenadorasDaClinica("coord-x:COORDENADOR_CLINICA");

        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        UUID.randomUUID(),
                        eventoId,
                        Tier.full_screen_alarm.name(),
                        "cg-b",
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        service.retrySweep(); // tenta cg-b
        service.retrySweep(); // escalona → coord-x
        service.retrySweep(); // tenta coord-x

        assertThat(alvosTentados())
                .as("enquanto a coordenadora não esgotou, o apoio não entra")
                .doesNotContain("apoio-a");
        assertThat(dispatchRepository.findByEventoId(eventoId))
                .extracting(NotificationDispatch::caregiverId)
                .doesNotContain("apoio-a");

        service.retrySweep(); // escalona → apoio-a
        assertThat(dispatchRepository.findByEventoId(eventoId))
                .as("agora sim, o último grau")
                .extracting(NotificationDispatch::caregiverId)
                .contains("apoio-a");
    }

    @Test
    @DisplayName("Coordenadora inalcançável não encerra a escada — o alarme segue para o apoio")
    void grauDaCoordenadoraIndisponivelNaoParaAEscada() {
        var service = serviceCom(semExpiracao(), 1, 0L);

        UUID eventoId = UUID.randomUUID();
        evento(eventoId, "emergencia", "cmd-emergencia", "Emergência", Instant.now());
        vinculosDoPaciente("apoio-a:PACIENTE_APOIO", "cg-b:CUIDADORA");
        // O adapter degrada para lista vazia quando o ACS recusa a listagem do tenant (hoje, 403
        // por falta de acs.cuidador.read). O grau 3 some; o grau 4 continua existindo.
        coordenadorasDaClinica();

        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        UUID.randomUUID(),
                        eventoId,
                        Tier.full_screen_alarm.name(),
                        "cg-b",
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        service.retrySweep();
        service.retrySweep();

        assertThat(dispatchRepository.findByEventoId(eventoId))
                .as("o apoio foi acionado apesar de o grau 3 não ter sido resolvido")
                .extracting(NotificationDispatch::caregiverId)
                .contains("apoio-a");
    }

    @Test
    @DisplayName(
            "Um degrau cuja destinatária saiu do directory não trava a escada — ela retoma em vez"
                    + " de parar num degrau fantasma")
    void degrauFantasmaNaoTravaAEscada() {
        var service = serviceCom(semExpiracao(), 1, 0L);

        UUID eventoId = UUID.randomUUID();
        evento(eventoId, "emergencia", "cmd-emergencia", "Emergência", Instant.now());
        // A escada de AGORA não contém mais quem esta linha estava tentando: a cuidadora foi
        // desvinculada do paciente entre uma tentativa e outra.
        vinculosDoPaciente("apoio-a:PACIENTE_APOIO", "cg-b:CUIDADORA");
        coordenadorasDaClinica();

        UUID fantasma = UUID.randomUUID();
        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        fantasma,
                        eventoId,
                        Tier.full_screen_alarm.name(),
                        "cg-que-saiu",
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        service.retrySweep(); // tentativa
        service.retrySweep(); // janela esgotada → precisa escalonar apesar do id desconhecido

        assertThat(dispatchRepository.findById(fantasma).orElseThrow().status())
                .isEqualTo(DispatchStatuses.ESCALATED);
        assertThat(dispatchRepository.findDueForRetry(Instant.now(), 10))
                .as("o alarme continua vivo, agora com alguém que existe na escada")
                .isNotEmpty()
                .allMatch(d -> List.of("cg-b", "apoio-a").contains(d.caregiverId()));
    }

    // -------------------------------------------------------------------------
    // Expiração por relevância clínica (ADR-011 §3)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Um alarme que expira por relevância deixa registro do motivo")
    void expiracaoPorRelevanciaDeixaRegistro() {
        var service =
                serviceCom(
                        new AlarmRelevancePolicy(
                                Map.of("rotina", Duration.ofHours(2)),
                                Set.of("ajuda", "socorro", "help")),
                        10,
                        0L);

        UUID eventoId = UUID.randomUUID();
        evento(
                eventoId,
                "rotina",
                "cmd-agua",
                "Quero água",
                Instant.now().minus(Duration.ofHours(5)));
        ladder(CG_A);
        UUID linha = UUID.randomUUID();
        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        linha,
                        eventoId,
                        Tier.discreet.name(),
                        CG_A,
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        var resultado = service.retrySweep();

        assertThat(resultado.expired()).isEqualTo(1);
        NotificationDispatch expirada = dispatchRepository.findById(linha).orElseThrow();
        assertThat(expirada.status()).isEqualTo(DispatchStatuses.FAILED);
        assertThat(expirada.outcomeReason())
                .as("FAILED sem motivo escrito é o silêncio que o ADR-011 proibiu")
                .isNotBlank()
                .contains("relevancia")
                .contains("rotina");
        verify(dispatchPort, never()).dispatch(any(), any());
    }

    @Test
    @DisplayName("Sem política definida para a urgência, o alarme não expira — o default é seguro")
    void semPoliticaNaoExpira() {
        var service = serviceCom(semExpiracao(), 10, 0L);

        UUID eventoId = UUID.randomUUID();
        evento(
                eventoId,
                "rotina",
                "cmd-agua",
                "Quero água",
                Instant.now().minus(Duration.ofDays(365)));
        ladder(CG_A);
        UUID linha = UUID.randomUUID();
        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        linha,
                        eventoId,
                        Tier.discreet.name(),
                        CG_A,
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        var resultado = service.retrySweep();

        assertThat(resultado.expired()).isZero();
        assertThat(dispatchRepository.findById(linha).orElseThrow().status())
                .isEqualTo(DispatchStatuses.NO_SESSION);
    }

    // -------------------------------------------------------------------------
    // O directory fora do ar nunca encerra um alarme
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Directory indisponível adia o escalonamento; o alarme permanece na fila")
    void directoryForaDoArNaoDescartaOAlarme() {
        var service = serviceCom(semExpiracao(), 1, 60_000L);

        UUID eventoId = UUID.randomUUID();
        evento(eventoId, "emergencia", "cmd-emergencia", "Emergência", Instant.now());
        when(caregiverRoutingPort.responsibleFor(PACIENTE))
                .thenThrow(new IllegalStateException("acs-service fora do ar"));

        UUID linha = UUID.randomUUID();
        dispatchRepository.save(
                NotificationDispatch.queuedFor(
                        linha,
                        eventoId,
                        Tier.full_screen_alarm.name(),
                        CG_A,
                        0,
                        EscalationGrade.CUIDADORA_ORIGINAL,
                        0,
                        null));

        service.retrySweep(); // tentativa
        service.retrySweep(); // tentaria escalonar, e não consegue

        NotificationDispatch mantida = dispatchRepository.findById(linha).orElseThrow();
        assertThat(mantida.status())
                .as("continua retentável — nem FAILED nem ESCALATED")
                .matches(DispatchStatuses::isRetryable);
        assertThat(mantida.nextAttemptAt()).as("volta ao sweep depois do backoff").isNotNull();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AlarmRelevancePolicy semExpiracao() {
        return new AlarmRelevancePolicy(Map.of(), Set.of("ajuda", "socorro", "help"));
    }

    private NotificationOrchestrationService serviceCom(
            AlarmRelevancePolicy policy, int attemptsPerRung, long roundBackoffMs) {
        return serviceCom(policy, attemptsPerRung, roundBackoffMs, 50);
    }

    private NotificationOrchestrationService serviceCom(
            AlarmRelevancePolicy policy, int attemptsPerRung, long roundBackoffMs, int batchSize) {
        return new NotificationOrchestrationService(
                dispatchRepository,
                eventoRepository,
                dispatchPort,
                caregiverRoutingPort,
                policy,
                batchSize,
                attemptsPerRung,
                roundBackoffMs);
    }

    private void evento(
            UUID id, String urgencia, String comandoRef, String label, Instant criadoEm) {
        when(eventoRepository.findById(id))
                .thenReturn(
                        Optional.of(
                                new Evento(
                                        id,
                                        PACIENTE,
                                        comandoRef,
                                        label,
                                        urgencia,
                                        "NAO_AVALIADO",
                                        null,
                                        null,
                                        criadoEm,
                                        criadoEm,
                                        1L,
                                        TENANT)));
    }

    /** Escada só de cuidadoras vinculadas ao paciente (graus 1 e 2). */
    private void ladder(String... cuidadoras) {
        when(caregiverRoutingPort.responsibleFor(PACIENTE))
                .thenReturn(
                        java.util.Arrays.stream(cuidadoras)
                                .map(
                                        id ->
                                                new CaregiverRoutingPort.ResponsibleCaregiver(
                                                        id, "CUIDADORA"))
                                .toList());
    }

    /** Vínculos do paciente com papéis explícitos — {@code id:PAPEL}. */
    private void vinculosDoPaciente(String... idsComPapel) {
        when(caregiverRoutingPort.responsibleFor(PACIENTE)).thenReturn(parse(idsComPapel));
    }

    /** Coordenadoras da clínica (grau 3), resolvidas fora do vínculo por paciente. */
    private void coordenadorasDaClinica(String... idsComPapel) {
        when(caregiverRoutingPort.clinicCoordinators(TENANT)).thenReturn(parse(idsComPapel));
    }

    private List<CaregiverRoutingPort.ResponsibleCaregiver> parse(String... idsComPapel) {
        return java.util.Arrays.stream(idsComPapel)
                .map(t -> t.split(":", 2))
                .map(t -> new CaregiverRoutingPort.ResponsibleCaregiver(t[0], t[1]))
                .toList();
    }

    /** Quem o dash-service foi convidado a notificar, em ordem. */
    private List<String> alvosTentados() {
        ArgumentCaptor<NotificationDispatch> captor =
                ArgumentCaptor.forClass(NotificationDispatch.class);
        verify(dispatchPort, org.mockito.Mockito.atLeastOnce()).dispatch(captor.capture(), any());
        return captor.getAllValues().stream().map(NotificationDispatch::caregiverId).toList();
    }
}
