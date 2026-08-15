package com.neuroassist.evnt.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface EventQueryUseCase {

    ActiveEventsResponse listActive();

    record ActiveEventsResponse(EventoSummary activeEvent) {

        public record EventoSummary(
                UUID eventoId,
                String urgencia,
                String tier,
                String feedback,
                Instant recognizedAt,
                String comandoRef) {}
    }
}
