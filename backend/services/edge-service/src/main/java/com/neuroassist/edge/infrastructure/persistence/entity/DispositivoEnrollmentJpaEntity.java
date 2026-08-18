package com.neuroassist.edge.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

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
public class DispositivoEnrollmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
}
