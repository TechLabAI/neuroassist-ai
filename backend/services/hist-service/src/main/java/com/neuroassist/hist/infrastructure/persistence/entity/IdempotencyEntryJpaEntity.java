package com.neuroassist.hist.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_entry")
// Schema resolvido via hibernate.default_schema por profile + TenantContextHolder em runtime
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyEntryJpaEntity {

    @Id
    @GeneratedValue
    @Column(name = "idempotency_entry_id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // TODO: complete fields from data-model.md
}
