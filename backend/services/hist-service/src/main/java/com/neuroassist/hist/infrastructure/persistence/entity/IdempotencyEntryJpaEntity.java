package com.neuroassist.hist.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * JPA entity — idempotency registry row keyed by {@code (fact_id, tenant_id)}. Written once per
 * successfully ingested audit fact; never updated or deleted (ADR-005).
 */
@Entity
@Table(
        name = "audit_idempotency",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_idempotency_fact_tenant",
                columnNames = {"fact_id", "tenant_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyEntryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "idempotency_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "fact_id", nullable = false, updatable = false)
    private UUID factId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "audit_record_id", nullable = false, updatable = false)
    private UUID auditRecordId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
}
