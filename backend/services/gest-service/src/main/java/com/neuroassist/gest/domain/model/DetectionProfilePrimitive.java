package com.neuroassist.gest.domain.model;

import java.util.UUID;

// No Spring/JPA imports (RULE-01, RULE-02)
public record DetectionProfilePrimitive(
        UUID id,
        UUID profileId,
        String tipoGestoId,
        String nome,
        String engineFeatureRef) {}
