package com.neuroassist.evnt.infrastructure.persistence.entity;

import com.neuroassist.evnt.domain.model.DispatchStatuses;
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
    private String status = DispatchStatuses.QUEUED;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    /** Degrau da escada de cuidadoras — 0 é a destinatária original (ADR-011 §1, V0502). */
    @Column(name = "escalation_order", nullable = false)
    @Builder.Default
    private int escalationOrder = 0;

    /**
     * Grau normativo da escada em que esta linha nasceu (ADR-011 §1, V0502). Texto, não ENUM
     * nativo: um ENUM do Postgres não aceita coerção implícita de varchar num INSERT, e foi assim
     * que toda gravação no outbox do PAC falhou em produção (C-12).
     */
    @Column(name = "escalation_grade", length = 50)
    private String escalationGrade;

    /** Passadas completas da escada já feitas por este alarme (ADR-011 §2, V0502). */
    @Column(name = "escalation_round", nullable = false)
    @Builder.Default
    private int escalationRound = 0;

    /** Backoff entre rodadas — nulo significa elegível já no próximo sweep (V0502). */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** Por que esta linha saiu da fila, em texto legível por quem investiga o incidente (V0502). */
    @Column(name = "outcome_reason", length = 500)
    private String outcomeReason;
}
