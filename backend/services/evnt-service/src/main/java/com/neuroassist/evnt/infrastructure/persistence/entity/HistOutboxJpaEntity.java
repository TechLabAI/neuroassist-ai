package com.neuroassist.evnt.infrastructure.persistence.entity;

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
 * JPA entity for {@code evnt_hist_outbox} (E2E-017).
 *
 * <p><b>The name carries the service prefix because it has to.</b> pac-service already owns a
 * {@code hist_outbox} in the same per-tenant schema, with a different shape. The per-module V
 * ranges separate migration numbers, not table names — the services share the schema.
 *
 * <p>Written inside the caller's {@code @Transactional} boundary, so the fact and the event that
 * produced it are committed together or not at all.
 *
 * <p>Schema: per-tenant. Column mapping aligned to {@code V0501__evnt_hist_outbox.sql} — {@code
 * status} is {@code varchar} there precisely because it is a {@code String} here (defect C-12).
 */
@Entity
@Table(name = "evnt_hist_outbox")
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

    /** Idempotency key of the fact — minted once, reused on every retry. */
    @Column(name = "fact_id", nullable = false, updatable = false)
    private UUID factId;

    @Column(name = "fact_type", nullable = false)
    private String factType;

    /** Serialized {@code AuditTrailClient.RecordAuditFactRequest}. */
    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    /** {@code pending} | {@code sent} | {@code failed}. */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /** Why the last attempt failed — kept so a {@code failed} entry can be triaged. */
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;
}
