package com.neuroassist.gest.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a new versioned DetectionProfile is published to the edge. Published
 * via ApplicationEventPublisher after the profile is persisted (in-process). ZERO Spring / ZERO JPA
 * (RULE-01, RULE-02).
 */
public record DetectionProfilePublished(UUID profileId, long version, Instant at) {}
