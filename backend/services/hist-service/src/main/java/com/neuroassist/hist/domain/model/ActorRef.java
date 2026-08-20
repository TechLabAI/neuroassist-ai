package com.neuroassist.hist.domain.model;

import java.util.UUID;

/**
 * Value object — snapshot of the caregiver who triggered the audit fact at the time of ingestion.
 * Stored as a denormalized snapshot so HIST never resolves ACS data during reads (ADR-005). Zero
 * Spring/JPA imports (RULE-01, RULE-02).
 */
public record ActorRef(UUID caregiverId, String displayNameSnapshot) {}
