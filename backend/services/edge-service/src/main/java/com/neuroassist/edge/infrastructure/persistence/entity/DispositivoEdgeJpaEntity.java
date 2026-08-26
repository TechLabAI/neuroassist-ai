package com.neuroassist.edge.infrastructure.persistence.entity;

import com.neuroassist.edge.domain.model.PairingState;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.springframework.data.domain.Persistable;

/**
 * JPA entity for {@code dispositivo_edge} — EDGE aggregate root. Schema is tenant-routed via {@link
 * com.neuroassist.edge.infrastructure.security.TenantContextHolder} and
 * MultiTenantConnectionProvider (search_path). NO {@code status} column — liveness is derived at
 * read time (ADR-006). {@code rowVersion} carries the optimistic-lock counter (@Version).
 */
@Entity
@Table(name = "dispositivo_edge")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispositivoEdgeJpaEntity implements Persistable<UUID> {

    /**
     * Assigned by the application ({@code DeviceRegistrationService} mints the UUID), not by the
     * provider — so there is no {@code @GeneratedValue}. Declaring one while also pre-assigning the
     * id made Spring Data treat every new device as detached: {@code isNew()} is id-based when the
     * {@code @Version} attribute is a primitive, so {@code save()} issued {@code merge()} → UPDATE
     * on a non-existent row → OptimisticLockException. Device registration never inserted a single
     * row against the real schema (E2E Sessão 3).
     *
     * <p>{@link #isNew()} below makes the decision explicit instead of inferred.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 60)
    private String tenantId;

    @Column(name = "label", length = 160)
    private String label;

    @Column(name = "paciente_id")
    private UUID pacienteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pairing_state", nullable = false, length = 20)
    private PairingState pairingState;

    @Column(name = "versao", length = 40)
    private String versao;

    @Column(name = "ultima_sincronizacao")
    private Instant ultimaSincronizacao;

    @Column(name = "running_profile_version")
    private Long runningProfileVersion;

    /** Optimistic-lock counter — mapped to {@code row_version} column (data-model.md §2.1). */
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    /**
     * Transient marker telling Spring Data whether this instance is an INSERT or an UPDATE.
     *
     * <p>Defaults to {@code true} so an entity built by the mapper (always from a domain object
     * that owns its id) inserts; {@link #markNotNew()} is invoked by the JPA lifecycle after load
     * or persist, so a re-saved entity updates.
     */
    @Transient @Builder.Default private boolean isNew = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 120, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    // -------------------------------------------------------------------------
    // Persistable — decide INSERT vs UPDATE explicitamente
    // -------------------------------------------------------------------------

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /** After load or persist the row exists, so subsequent saves must UPDATE. */
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
