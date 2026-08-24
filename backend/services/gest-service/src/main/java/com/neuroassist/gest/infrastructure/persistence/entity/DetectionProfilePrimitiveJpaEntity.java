package com.neuroassist.gest.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * JPA entity for the {@code detection_profile_primitive} table — snapshot member of a profile.
 * Immutable after creation (no @Setter). Deleted cascade when the parent profile is deleted (FK ON
 * DELETE CASCADE). Schema managed by neuroassist-ai-migrations (V0400). Multi-tenancy via schema
 * routing (no tenant_id column).
 *
 * <p>Implements {@link Persistable} — id is domain-assigned, not DB-generated; see
 * TipoGestoJpaEntity's javadoc for why this is required.
 */
@Entity
@Table(name = "detection_profile_primitive")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectionProfilePrimitiveJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Transient @Builder.Default private boolean isNew = true;

    @Column(name = "detection_profile_id", nullable = false, updatable = false)
    private UUID detectionProfileId;

    @Column(name = "tipo_gesto_id", nullable = false, updatable = false)
    private UUID tipoGestoId;

    @Column(name = "nome_snapshot", nullable = false, length = 120, updatable = false)
    private String nomeSnapshot;

    @Column(name = "engine_feature_ref", nullable = false, length = 200, updatable = false)
    private String engineFeatureRef;

    /**
     * Faixa de sensibilidade deste gesto no perfil (V0401).
     *
     * <p>Guardado como texto e nao como {@code @Enumerated}: a coluna e {@code text} com
     * {@code CHECK (sensibilidade IN ('baixa','media','alta'))}, e o mapeamento fica no adapter.
     * Um ENUM do Postgres mapeado como String foi o que quebrou toda escrita de outbox neste
     * repositorio — aqui a coluna e texto de verdade dos dois lados.
     */
    @Column(name = "sensibilidade", nullable = false, length = 10, updatable = false)
    private String sensibilidade;

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
