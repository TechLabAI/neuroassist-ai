package com.neuroassist.hist.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
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
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_audit_record_fact_tenant",
                        columnNames = {"fact_id", "tenant_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditRecordJpaEntity {

    @Id
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

    /**
     * AES-256-GCM ciphertext of the JSON payload, encoded as "base64(iv):base64(ciphertext)". Null
     * when the original payload was null. After LGPD erasure the decryption key is gone — this
     * column retains the ciphertext (harmless without key) and {@code updatable = false} ensures it
     * is never overwritten (ADR-004).
     */
    @Column(name = "payload", columnDefinition = "text", updatable = false)
    private String payload;

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

    /** Patient scope for caregiver reads (C-16). See {@code AuditRecord#scopePacienteId}. */
    @Column(name = "scope_paciente_id", updatable = false)
    private UUID scopePacienteId;
}
