package com.neuroassist.evnt.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "report_idempotency")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportIdempotencyJpaEntity {

    @Id
    @Column(name = "report_id", length = 64)
    private String reportId;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
