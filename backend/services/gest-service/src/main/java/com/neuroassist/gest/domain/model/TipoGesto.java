package com.neuroassist.gest.domain.model;

import java.time.Instant;

// No Spring/JPA imports (RULE-01, RULE-02)
public record TipoGesto(
        String id,
        String nome,
        String detectabilityState,
        String engineFeatureRef,
        Instant createdAt,
        Instant updatedAt) {}
