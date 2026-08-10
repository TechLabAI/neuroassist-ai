package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.EventConfirmationUseCase;
import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.domain.model.Evento;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventConfirmationService implements EventConfirmationUseCase {

    private final EventoRepositoryPort eventoRepositoryPort;
    private final AuditTrailPort auditTrailPort;

    @Override
    public void confirm(UUID eventoId, String verdict) {
        Evento evento =
                eventoRepositoryPort
                        .findById(eventoId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Evento not found"));

        if (!"NAO_AVALIADO".equals(evento.feedback())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Evento already confirmed");
        }

        String feedback = "real".equalsIgnoreCase(verdict) ? "REAL" : "FALSO_POSITIVO";
        Evento updated =
                new Evento(
                        evento.id(),
                        evento.pacienteId(),
                        evento.comandoRef(),
                        evento.commandLabelSnapshot(),
                        evento.urgenciaSnapshot(),
                        feedback,
                        "cuidadora-jamilly-fixo",
                        Instant.now(),
                        evento.recognizedAt(),
                        evento.createdAt());

        eventoRepositoryPort.save(updated);
        auditTrailPort.append("EVENTO_CONFIRMADO", eventoId.toString(), "verdict=" + verdict);
        log.info("Evento confirmed eventoId={} feedback={}", eventoId, feedback);
    }
}
