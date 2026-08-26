package com.neuroassist.edge.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.springframework.data.domain.Persistable;

/**
 * JPA entity for {@code dispositivo_enrollment} — one-time, TTL-bounded bootstrap-trust token. Only
 * the SHA-256 hash is stored — never the plaintext token (data-model.md §2.2, ADR-005). Schema is
 * tenant-routed via MultiTenantConnectionProvider.
 */
@Entity
@Table(name = "dispositivo_enrollment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispositivoEnrollmentJpaEntity implements Persistable<UUID> {

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

    @Column(name = "tenant_id", nullable = false, length = 60)
    private String tenantId;

    @Column(name = "dispositivo_edge_id", nullable = false, updatable = false)
    private UUID dispositivoEdgeId;

    /** SHA-256 hash of the enrollment token — plaintext never stored (ADR-005). */
    @Column(name = "token_hash", nullable = false, length = 200, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Set atomically when enrollment is consumed (data-model.md §2.2 invariant). */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
