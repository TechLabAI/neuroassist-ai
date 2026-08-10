package com.neuroassist.gest.api.dto;

import java.time.Instant;

public record TipoGestoResponse(
        String id,
        String nome,
        String detectabilityState,
        String engineFeatureRef,
        Instant createdAt,
        Instant updatedAt) {}
