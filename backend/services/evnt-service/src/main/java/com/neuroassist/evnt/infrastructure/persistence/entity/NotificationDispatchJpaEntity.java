package com.neuroassist.evnt.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "notification_dispatch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDispatchJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "tier", nullable = false, length = 50)
    private String tier;

    @Column(name = "target_caregiver_id", length = 255)
    private String targetCaregiverId;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "QUEUED";

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
}
