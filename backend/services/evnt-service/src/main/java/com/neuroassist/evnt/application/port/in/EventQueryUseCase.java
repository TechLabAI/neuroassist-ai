package com.neuroassist.evnt.application.port.in;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventQueryUseCase {

    ActiveEventsResponse listActive();

    /**
     * Responde, para um conjunto de pacientes, quais têm pedido de ajuda em aberto.
     *
     * <p><b>Por que esta pergunta precisou existir.</b> O painel da cuidadora quer acender um
     * indicador por linha — "este paciente pediu ajuda e ninguém atendeu ainda". Com a API anterior
     * isso era <i>irrespondível</i>: {@link #listActive()} devolve <b>um</b> evento e o seu resumo
     * nem sequer carrega o {@code pacienteId}. Não era um stub por ligar; era capacidade ausente.
     *
     * <p>A forma é de conjunto, e não de um paciente por chamada, porque o painel lista N
     * pacientes: um endpoint por paciente seria um N+1 sobre HTTP no caminho mais quente do
     * sistema.
     */
    ActiveEventsByPatientResponse listActiveByPatient(Collection<String> pacienteIds);

    record ActiveEventsResponse(EventoSummary activeEvent) {

        public record EventoSummary(
                UUID eventoId,
                String urgencia,
                String tier,
                String feedback,
                Instant recognizedAt,
                String comandoRef) {}
    }

    /**
     * @param consultados exatamente os ids que a consulta cobriu — devolvido de volta de propósito:
     *     o consumidor consegue distinguir "perguntei por este paciente e não há alarme" de "este
     *     paciente nem entrou na pergunta". Sem isso, um id ausente da resposta seria ambíguo.
     * @param comAlarmeAtivo subconjunto de {@code consultados} com ao menos um evento {@code
     *     NAO_AVALIADO}
     */
    record ActiveEventsByPatientResponse(List<String> consultados, List<String> comAlarmeAtivo) {}
}
