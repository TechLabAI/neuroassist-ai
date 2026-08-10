package com.neuroassist.gest.domain.model;

import java.time.Instant;
import java.util.UUID;

// No Spring/JPA imports (RULE-01, RULE-02)
public record DetectionProfile(
        UUID id,
        int version,
        boolean active,
        String contractVersion,
        Instant publishedAt) {}
