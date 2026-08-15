package com.neuroassist.evnt.domain.model;

import java.time.Instant;
import java.util.UUID;

// Zero Spring/JPA imports (RULE-01, RULE-02)
public record Evento(
        UUID id,
        String pacienteId,
        String comandoRef,
        String commandLabelSnapshot,
        String urgenciaSnapshot,
        String feedback,
        String confirmadoPor,
        Instant confirmadoAt,
        Instant recognizedAt,
        Instant createdAt,
        Long seq,
        String tenantId) {}
