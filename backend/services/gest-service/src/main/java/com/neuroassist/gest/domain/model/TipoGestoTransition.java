package com.neuroassist.gest.domain.model;

import java.time.Instant;
import java.util.UUID;

// No Spring/JPA imports (RULE-01, RULE-02)
public record TipoGestoTransition(
        UUID id,
        String tipoGestoId,
        String fromState,
        String toState,
        String actor,
        String reason,
        Instant at) {}
