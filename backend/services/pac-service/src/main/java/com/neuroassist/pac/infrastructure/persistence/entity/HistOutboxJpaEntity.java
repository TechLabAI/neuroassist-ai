package com.neuroassist.pac.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the {@code hist_outbox} table (INT-PAC-004).
 *
 * <p>Stores pending domain facts for hist-service when direct delivery fails. Written within the
 * caller's {@code @Transactional} boundary for atomic durability (lifecycle change + outbox entry
 * committed together, never separately).
 *
 * <p>Schema: per-empresa (tenant-scoped). Column mapping aligned to V0205__pac_hist_outbox.sql.
 */
@Entity
@Table(name = "hist_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistOutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Domain event type: {@code comando.published | comando.recalibrated | comando.retired}. */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * JSON-serialized {@link com.neuroassist.pac.infrastructure.clients.HistFactRequest} payload.
     */
    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    /**
     * Delivery status. Values: {@code pending} (awaiting delivery), {@code sent} (delivered to
     * hist-service), {@code failed} (max retries exceeded).
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "pending";

    /** How many delivery attempts have been made (including the initial attempt). */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Populated when {@code status} transitions to {@code sent}. */
    @Column(name = "sent_at")
    private Instant sentAt;
}
