package com.neuroassist.acs.domain.model;

import java.time.Instant;
import java.util.UUID;

// TODO: complete fields from domain-model-diagram.puml — no Spring/JPA imports (RULE-01, RULE-02)
public record Tenant(UUID id, String tenantId, Instant createdAt
        // TODO: add domain fields
        ) {}
