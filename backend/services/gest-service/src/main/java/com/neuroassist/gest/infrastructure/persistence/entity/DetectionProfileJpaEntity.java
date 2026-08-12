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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * JPA entity for the {@code detection_profile} table — immutable versioned detection profile. Only
 * {@code active} may be set to false after creation (via deactivateCurrent). Schema managed by
 * neuroassist-ai-migrations (V0400). Multi-tenancy via schema routing (no tenant_id column).
 *
 * <p>{@code classification_params} is stored as JSONB; the adapter layer handles
 * serialization/deserialization to/from {@code Map<String,Object>} (P3).
 *
 * <p>Implements {@link Persistable} — id is domain-assigned, not DB-generated; see
 * TipoGestoJpaEntity's javadoc for why this is required.
 */
@Entity
@Table(name = "detection_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectionProfileJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Transient @Builder.Default private boolean isNew = true;

    @Column(name = "version", nullable = false, unique = true, updatable = false)
    private Long version;

    /** Only column that can change after insert — flipped to false by deactivateCurrent(). */
    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "contract_version", nullable = false, length = 20, updatable = false)
    private String contractVersion;

    /**
     * Stored as JSONB (object). Serialized as a JSON string by the adapter. See {@code
     * ck_profile_params_object} in the DB schema.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} makes Hibernate bind this as jsonb at the JDBC level
     * — {@code columnDefinition} alone only affects DDL, not parameter binding, so without it the
     * driver rejects the insert ("column is of type jsonb but expression is of type character
     * varying").
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "classification_params",
            nullable = false,
            columnDefinition = "jsonb",
            updatable = false)
    private String classificationParams;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "published_by", length = 120, updatable = false)
    private String publishedBy;

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
