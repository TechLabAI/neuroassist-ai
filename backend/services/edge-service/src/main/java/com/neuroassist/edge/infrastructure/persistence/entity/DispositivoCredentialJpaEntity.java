package com.neuroassist.edge.infrastructure.persistence.entity;

import com.neuroassist.edge.domain.model.CredentialStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.springframework.data.domain.Persistable;

/**
 * JPA entity for {@code dispositivo_credential} — per-device OAuth2 client_credentials material.
 * Only {@code secret_hash} is stored — never plaintext (data-model.md §2.3, ADR-005). At most one
 * {@code active} credential per device, enforced by partial unique index {@code
 * ux_dispositivo_credential_active}. Schema is tenant-routed via MultiTenantConnectionProvider.
 */
@Entity
@Table(name = "dispositivo_credential")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispositivoCredentialJpaEntity implements Persistable<UUID> {

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

    @Column(name = "client_id", nullable = false, length = 120, updatable = false)
    private String clientId;

    /** SHA-256 hash of the OAuth2 client secret — plaintext never stored (ADR-005). */
    @Column(name = "secret_hash", nullable = false, length = 200, updatable = false)
    private String secretHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CredentialStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    /** Stamped when revoked at retire or re-pair (data-model.md §2.3 invariant). */
    @Column(name = "revoked_at")
    private Instant revokedAt;

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
