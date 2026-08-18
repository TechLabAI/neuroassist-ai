package com.neuroassist.edge.infrastructure.persistence.entity;

import com.neuroassist.edge.domain.model.CredentialStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

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
public class DispositivoCredentialJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
}
