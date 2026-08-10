package com.neuroassist.evnt.domain.model;

import java.time.Instant;
import java.util.UUID;

// Zero Spring/JPA imports (RULE-01, RULE-02)
public record ReportIdempotency(String reportId, UUID eventoId, Instant processedAt) {}
