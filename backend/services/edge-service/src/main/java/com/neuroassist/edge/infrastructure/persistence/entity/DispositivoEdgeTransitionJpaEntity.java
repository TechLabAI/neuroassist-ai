package com.neuroassist.edge.infrastructure.persistence.entity;

import com.neuroassist.edge.domain.model.TransitionKind;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * JPA entity for {@code dispositivo_edge_transition} — append-only audit of pairing-lifecycle and
 * liveness transitions (data-model.md §2.4, ADR-005 invariant #3). No UPDATE or DELETE operations
 * are ever issued against this table. Schema is tenant-routed via MultiTenantConnectionProvider.
 */
@Entity
@Table(name = "dispositivo_edge_transition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispositivoEdgeTransitionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 60, updatable = false)
    private String tenantId;

    @Column(name = "dispositivo_edge_id", nullable = false, updatable = false)
    private UUID dispositivoEdgeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private TransitionKind kind;

    @Column(name = "from_state", length = 20, updatable = false)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 20, updatable = false)
    private String toState;

    @Column(name = "actor", nullable = false, length = 120, updatable = false)
    private String actor;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;
}
