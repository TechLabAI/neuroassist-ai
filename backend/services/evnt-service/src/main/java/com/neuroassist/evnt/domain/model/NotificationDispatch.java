package com.neuroassist.evnt.domain.model;

import java.time.Instant;
import java.util.UUID;

// Zero Spring/JPA imports (RULE-01, RULE-02)
public record NotificationDispatch(
        UUID id,
        UUID eventoId,
        String tier,
        String caregiverId,
        String status,
        int attempts,
        Instant lastAttemptAt) {}
