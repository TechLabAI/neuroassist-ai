package com.neuroassist.edge.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Idempotency tracking row — one per processed event id. Generated from events.idempotency (D-065).
 * Consumers persist this row in the same transaction as their side effects.
 *
 * <pre>
 *   if (processedEventRepo.existsById(envelope.eventId())) {
 *       return;  // already processed — JMS redelivery, no-op
 *   }
 *   // ... domain side effects ...
 *   processedEventRepo.save(new ProcessedEvent(envelope.eventId(), envelope.eventType()));
 * </pre>
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventid;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    public ProcessedEvent() {}

    public ProcessedEvent(UUID id, String eventType) {
        this.eventid = id;
        this.eventType = eventType;
        this.processedAt = OffsetDateTime.now();
    }

    public UUID id() {
        return eventid;
    }

    public String eventType() {
        return eventType;
    }

    public OffsetDateTime processedAt() {
        return processedAt;
    }
}
