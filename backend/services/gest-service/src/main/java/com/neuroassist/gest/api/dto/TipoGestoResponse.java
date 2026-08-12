package com.neuroassist.gest.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Response shape for a TipoGesto resource. */
public record TipoGestoResponse(
        UUID id,
        String nome,
        String detectabilityState,
        String engineFeatureRef,
        Instant createdAt,
        Instant updatedAt) {}
