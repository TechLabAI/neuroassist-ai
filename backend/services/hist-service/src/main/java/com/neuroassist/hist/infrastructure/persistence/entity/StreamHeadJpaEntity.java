package com.neuroassist.hist.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * JPA entity — mutable stream-tip cache row for hash-chain append serialisation (ADR-006).
 * Updated on each successful append to the same {@code (stream_key, tenant_id)} stream. Distinct
 * from audit_record which is write-once (ADR-004).
 */
@Entity
@Table(
        name = "stream_head",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_stream_head_key_tenant",
                columnNames = {"stream_key", "tenant_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamHeadJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "stream_head_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "stream_key", nullable = false)
    private String streamKey;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "current_sequence_no", nullable = false)
    private long currentSequenceNo;

    @Column(name = "current_hash", nullable = false, length = 64)
    private String currentHash;

    @Column(name = "last_recorded_at", nullable = false)
    private Instant lastRecordedAt;
}
