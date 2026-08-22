package com.neuroassist.edge.infrastructure.persistence.entity;

import com.neuroassist.edge.domain.model.TransitionKind;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.springframework.data.domain.Persistable;

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
public class DispositivoEdgeTransitionJpaEntity implements Persistable<UUID> {

    /**
     * Assigned by the application, not by the provider — so there is no {@code @GeneratedValue}.
     * With a pre-assigned id, Spring Data's {@code isNew()} is id-based and {@code save()} issues
     * {@code merge()} instead of {@code persist()}: a wasted SELECT and, on a row that does not
     * exist yet, an UPDATE affecting 0 rows → {@code ObjectOptimisticLockingFailureException} (E2E
     * Sessão 3, C-20). {@link #isNew()} makes the INSERT/UPDATE decision explicit.
     */
    @Id
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

    // -------------------------------------------------------------------------
    // Persistable — id é atribuído pela aplicação, então a decisão é explícita
    // -------------------------------------------------------------------------

    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    /**
     * Told by the repository adapter when the row already exists, so {@code save()} performs an
     * UPDATE (merge) instead of an INSERT (persist). The mapper always builds a fresh instance from
     * the domain object, so the entity cannot know this on its own.
     */
    public void markExisting() {
        this.isNew = false;
    }
}
