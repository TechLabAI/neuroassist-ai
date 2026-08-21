package com.neuroassist.hist.domain.model;

import java.util.UUID;

/**
 * Value object — the entity that an audit fact is about (patient, command, caregiver, device).
 * Opaque ref owned by the producer; HIST never resolves it (ADR-005). Zero Spring/JPA imports
 * (RULE-01, RULE-02).
 */
public record SubjectRef(SubjectType type, UUID id) {

    /** Derives the canonical stream key for this subject. */
    public String streamKey() {
        return type.name() + ":" + id;
    }
}
