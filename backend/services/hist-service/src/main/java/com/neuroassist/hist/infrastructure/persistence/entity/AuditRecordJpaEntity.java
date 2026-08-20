package com.neuroassist.hist.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;

/**
 * JPA entity — write-once audit record row in the tenant ledger (ADR-004). All business columns are
 * {@code updatable = false} to enforce immutability at the persistence layer. Payload is stored as
 * JSON text via {@link JsonMapConverter}.
 */
@Entity
@Table(
        name = "audit_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_audit_record_fact_tenant",
                columnNames = {"fact_id", "tenant_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditRecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_record_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "fact_id", nullable = false, updatable = false)
    private UUID factId;

    @Column(name = "fact_type", nullable = false, updatable = false)
    private String factType;

    // Subject fields (from SubjectRef value object)
    @Column(name = "subject_type", nullable = false, updatable = false)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    // Actor fields (from ActorRef value object — denormalised snapshot)
    @Column(name = "caregiver_id", nullable = false, updatable = false)
    private UUID caregiverId;

    @Column(name = "actor_display_name", updatable = false)
    private String actorDisplayName;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "payload", columnDefinition = "text", updatable = false)
    private Map<String, Object> payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "stream_key", nullable = false, updatable = false)
    private String streamKey;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private long sequenceNo;

    @Column(name = "prev_hash", nullable = false, updatable = false, length = 64)
    private String prevHash;

    @Column(name = "record_hash", nullable = false, updatable = false, length = 64)
    private String recordHash;

    @Column(name = "corrects_record_id", updatable = false)
    private UUID correctsRecordId;
}
