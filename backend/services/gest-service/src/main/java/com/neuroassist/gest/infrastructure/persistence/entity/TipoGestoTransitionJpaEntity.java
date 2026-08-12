package com.neuroassist.gest.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * JPA entity for the {@code tipo_gesto_transition} table — append-only audit log (invariant #4).
 * No @Setter — records are never mutated by the application once written. Schema managed by
 * neuroassist-ai-migrations (V0400). Multi-tenancy via schema routing (no tenant_id column).
 *
 * <p>Implements {@link Persistable} — id is domain-assigned, not DB-generated; see
 * TipoGestoJpaEntity's javadoc for why this is required.
 */
@Entity
@Table(name = "tipo_gesto_transition")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoGestoTransitionJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Transient @Builder.Default private boolean isNew = true;

    @Column(name = "tipo_gesto_id", nullable = false, updatable = false)
    private UUID tipoGestoId;

    /**
     * Previous state (nullable for the initial REGISTERED transition where there is no from_state).
     * Stored as varchar(20) matching DB CHECK constraint values.
     */
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

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
