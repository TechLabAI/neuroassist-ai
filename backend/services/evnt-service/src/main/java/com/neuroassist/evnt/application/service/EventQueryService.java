package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.EventQueryUseCase;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.domain.model.Evento;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventQueryService implements EventQueryUseCase {

    private final EventoRepositoryPort eventoRepositoryPort;

    @Override
    public ActiveEventsResponse listActive() {
        List<Evento> eventos = eventoRepositoryPort.findByFeedback("NAO_AVALIADO");
        if (eventos.isEmpty()) {
            return new ActiveEventsResponse(null);
        }
        Evento first = eventos.get(0);
        String tier =
                "emergencia".equalsIgnoreCase(first.urgenciaSnapshot())
                        ? "full_screen_alarm"
                        : "discreet";
        ActiveEventsResponse.EventoSummary summary =
                new ActiveEventsResponse.EventoSummary(
                        first.id(),
                        first.urgenciaSnapshot(),
                        tier,
                        first.feedback(),
                        first.recognizedAt(),
                        first.comandoRef());
        return new ActiveEventsResponse(summary);
    }
}
