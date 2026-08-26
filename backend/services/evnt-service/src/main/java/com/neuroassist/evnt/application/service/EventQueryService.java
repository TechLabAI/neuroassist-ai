package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.EventQueryUseCase;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.domain.model.Feedback;
import com.neuroassist.evnt.domain.model.Tier;
import com.neuroassist.evnt.shared.error.CodedException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventQueryService implements EventQueryUseCase {

    /**
     * Teto de ids por consulta.
     *
     * <p>O escopo de uma cuidadora tem dezenas de pacientes, não milhares. O limite existe para que
     * um chamador enganado não transforme esta consulta numa varredura da tabela inteira — e é
     * <b>recusa</b>, nunca truncamento silencioso: truncar devolveria uma resposta que parece
     * completa e esconde alarmes.
     */
    static final int MAX_PACIENTES_POR_CONSULTA = 200;

    private final EventoRepositoryPort eventoRepositoryPort;

    @Override
    public ActiveEventsResponse listActive() {
        List<Evento> eventos =
                eventoRepositoryPort.findByFeedbackMaisRecentePrimeiro(
                        Feedback.NAO_AVALIADO.name());
        if (eventos.isEmpty()) {
            return new ActiveEventsResponse(null);
        }
        // O mais recente, agora por contrato da porta e não por acaso do plano de execução.
        Evento maisRecente = eventos.get(0);
        String tier =
                "emergencia".equalsIgnoreCase(maisRecente.urgenciaSnapshot())
                        ? Tier.full_screen_alarm.name()
                        : Tier.discreet.name();
        ActiveEventsResponse.EventoSummary summary =
                new ActiveEventsResponse.EventoSummary(
                        maisRecente.id(),
                        maisRecente.urgenciaSnapshot(),
                        tier,
                        maisRecente.feedback(),
                        maisRecente.recognizedAt(),
                        maisRecente.comandoRef());
        return new ActiveEventsResponse(summary);
    }

    @Override
    @Transactional(readOnly = true)
    public ActiveEventsByPatientResponse listActiveByPatient(Collection<String> pacienteIds) {
        List<String> consultados = normalizar(pacienteIds);
        if (consultados.isEmpty()) {
            return new ActiveEventsByPatientResponse(List.of(), List.of());
        }
        if (consultados.size() > MAX_PACIENTES_POR_CONSULTA) {
            throw new CodedException(
                            "too-many-patients",
                            "pacienteIds acima do teto de "
                                    + MAX_PACIENTES_POR_CONSULTA
                                    + " por consulta")
                    .withContext(
                            Map.of(
                                    "recebidos",
                                    consultados.size(),
                                    "maximo",
                                    MAX_PACIENTES_POR_CONSULTA));
        }

        List<String> comAlarme =
                eventoRepositoryPort.findPacienteIdsByFeedback(
                        Feedback.NAO_AVALIADO.name(), consultados);
        log.debug(
                "listActiveByPatient consultados={} comAlarmeAtivo={}",
                consultados.size(),
                comAlarme.size());
        return new ActiveEventsByPatientResponse(consultados, List.copyOf(comAlarme));
    }

    /** Sem nulos, sem brancos, sem repetidos — preservando a ordem em que o chamador perguntou. */
    private static List<String> normalizar(Collection<String> pacienteIds) {
        if (pacienteIds == null) {
            return List.of();
        }
        LinkedHashSet<String> unicos = new LinkedHashSet<>();
        for (String id : pacienteIds) {
            if (id != null && !id.isBlank()) {
                unicos.add(id.trim());
            }
        }
        return List.copyOf(unicos);
    }
}
