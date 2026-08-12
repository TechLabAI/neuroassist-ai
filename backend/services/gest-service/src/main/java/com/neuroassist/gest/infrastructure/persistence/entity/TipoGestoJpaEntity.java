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
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/**
 * JPA entity for the {@code tipo_gesto} table (tenant-scoped via schema routing). Schema managed by
 * neuroassist-ai-migrations (V0400). Field names mirror the SQL columns from
 * V1__create_initial_schema.sql. Multi-tenancy: no tenant_id column — isolation is via {@code
 * search_path} set by MultiTenantConnectionProvider.
 *
 * <p>Implements {@link Persistable} because the id is assigned client-side by the domain ({@code
 * TipoGesto.register()}), not by the database — without it, Spring Data JPA's default "id == null
 * means new" heuristic mistakes every first save for an update and calls {@code merge()} on a row
 * that doesn't exist yet, throwing StaleObjectStateException.
 *
 * <p>No {@code @GeneratedValue} — the id is always assigned by the domain before the adapter maps
 * it here; Hibernate's UUID generator would otherwise treat a pre-set id as a detached entity and
 * reject the first persist() (InvalidDataAccessApiUsageException).
 */
@Entity
@Table(name = "tipo_gesto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoGestoJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Transient @Builder.Default private boolean isNew = true;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    /**
     * Stored as varchar(20) matching the DB CHECK constraint values: 'registered',
     * 'pending_training', 'supported', 'deprecated'. Conversion to/from DetectabilityState enum is
     * done in the adapter layer (P3).
     */
    @Column(name = "detectability_state", nullable = false, length = 20)
    private String detectabilityState;

    @Column(name = "engine_feature_ref", length = 200)
    private String engineFeatureRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

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
