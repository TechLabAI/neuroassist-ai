package com.neuroassist.hist.domain.model;

import java.util.UUID;

// TODO: complete fields — no Spring/JPA imports (RULE-01, RULE-02)
public record IdempotencyEntry(
    UUID id
    // TODO: add domain fields
) {}
