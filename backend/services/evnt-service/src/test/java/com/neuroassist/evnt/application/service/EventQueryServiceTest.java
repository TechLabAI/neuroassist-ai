package com.neuroassist.evnt.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.evnt.application.port.in.EventQueryUseCase.ActiveEventsByPatientResponse;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.domain.model.Feedback;
import com.neuroassist.evnt.shared.error.CodedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * "Este paciente tem pedido de ajuda em aberto?" — a pergunta que o painel da cuidadora precisa
 * fazer e que a API do EVNT não sabia responder.
 *
 * <p><b>Controle negativo de cada asserção.</b> Um teste que só verificasse {@code
 * comAlarmeAtivo.isEmpty()} passaria com o defeito vivo — era exatamente esse o estado anterior, em
 * que o painel devolvia {@code hasActiveEvent=false} para todo mundo, sempre. Por isso cada cenário
 * aqui exige o <b>true</b>: o paciente que tem alarme aparece, e o que não tem fica de fora.
 */
class EventQueryServiceTest {

    private EventoRepositoryPort repository;
    private EventQueryService service;

    private static final String MARCIO = "marcio";
    private static final String JOANA = "joana";

    @BeforeEach
    void setUp() {
        repository = mock(EventoRepositoryPort.class);
        service = new EventQueryService(repository);
    }

    @Test
    @DisplayName("o paciente com evento NAO_AVALIADO aparece em comAlarmeAtivo; o outro, não")
    void pacienteComAlarmeApareceEOutroNao() {
        when(repository.findPacienteIdsByFeedback(eq(Feedback.NAO_AVALIADO.name()), any()))
                .thenReturn(List.of(MARCIO));

        ActiveEventsByPatientResponse resposta =
                service.listActiveByPatient(List.of(MARCIO, JOANA));

        assertThat(resposta.comAlarmeAtivo()).containsExactly(MARCIO);
        assertThat(resposta.comAlarmeAtivo()).doesNotContain(JOANA);
        // `consultados` devolve a pergunta: ausência de JOANA aqui significaria "nem perguntei",
        // que é diferente de "perguntei e não há".
        assertThat(resposta.consultados()).containsExactly(MARCIO, JOANA);
    }

    @Test
    @DisplayName("uma única ida ao repositório para N pacientes — nada de N+1")
    void umaConsultaSoParaTodosOsPacientes() {
        when(repository.findPacienteIdsByFeedback(any(), any())).thenReturn(List.of());

        service.listActiveByPatient(List.of("p1", "p2", "p3", "p4", "p5"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        // Uma invocação, com os cinco ids juntos. Se alguém trocar isto por um laço, o
        // verify(times(1)) implícito do captor falha.
        verify(repository)
                .findPacienteIdsByFeedback(eq(Feedback.NAO_AVALIADO.name()), captor.capture());
        assertThat(captor.getValue()).containsExactly("p1", "p2", "p3", "p4", "p5");
    }

    @Test
    @DisplayName("ids repetidos, brancos e nulos são normalizados antes da consulta")
    void normalizaEntrada() {
        when(repository.findPacienteIdsByFeedback(any(), any())).thenReturn(List.of());

        service.listActiveByPatient(java.util.Arrays.asList(MARCIO, "  ", MARCIO, null, JOANA));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).findPacienteIdsByFeedback(any(), captor.capture());
        assertThat(captor.getValue()).containsExactly(MARCIO, JOANA);
    }

    @Test
    @DisplayName("conjunto vazio não vira consulta — e nunca vira 'todos'")
    void conjuntoVazioNaoConsulta() {
        ActiveEventsByPatientResponse resposta = service.listActiveByPatient(List.of());

        assertThat(resposta.consultados()).isEmpty();
        assertThat(resposta.comAlarmeAtivo()).isEmpty();
        verify(repository, never()).findPacienteIdsByFeedback(any(), any());
    }

    @Test
    @DisplayName("acima do teto recusa em vez de truncar — resposta truncada esconderia alarmes")
    void acimaDoTetoRecusa() {
        List<String> demais =
                IntStream.rangeClosed(0, EventQueryService.MAX_PACIENTES_POR_CONSULTA)
                        .mapToObj(i -> "p" + i)
                        .toList();

        assertThatThrownBy(() -> service.listActiveByPatient(demais))
                .isInstanceOf(CodedException.class)
                .extracting(ex -> ((CodedException) ex).code())
                .isEqualTo("too-many-patients");

        verify(repository, never()).findPacienteIdsByFeedback(any(), any());
    }

    @Test
    @DisplayName("listActive devolve o evento mais recente, e não o que o banco entregar primeiro")
    void listActiveUsaOMaisRecente() {
        Evento maisRecente = evento("emergencia", 42L);
        Evento antigo = evento("rotina", 7L);
        // A porta contrata "mais recente primeiro"; o serviço confia nisso e pega o primeiro.
        when(repository.findByFeedbackMaisRecentePrimeiro(Feedback.NAO_AVALIADO.name()))
                .thenReturn(List.of(maisRecente, antigo));

        var resposta = service.listActive();

        assertThat(resposta.activeEvent().eventoId()).isEqualTo(maisRecente.id());
        assertThat(resposta.activeEvent().tier()).isEqualTo("full_screen_alarm");
    }

    private static Evento evento(String urgencia, long seq) {
        return new Evento(
                UUID.randomUUID(),
                MARCIO,
                "cmd-ajuda",
                "Preciso de ajuda",
                urgencia,
                Feedback.NAO_AVALIADO.name(),
                null,
                null,
                Instant.now(),
                Instant.now(),
                seq,
                "familia-amaral");
    }
}
