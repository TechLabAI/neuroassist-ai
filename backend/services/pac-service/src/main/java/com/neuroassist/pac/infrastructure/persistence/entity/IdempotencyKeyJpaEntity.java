package com.neuroassist.pac.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the {@code idempotency_key} table (PAC-023, SPEC-05 §3.3).
 *
 * <p>Schema: per-empresa (tenant-scoped). Per-schema routing via {@link
 * com.neuroassist.pac.infrastructure.security.MultiTenantConnectionProvider} + search_path provides
 * tenant isolation — the composite key column does NOT include tenantId.
 *
 * <p>Column mapping aligned to {@code V0206__pac_idempotency_key.sql}. Rows older than 24h are
 * eligible for lazy purge by {@link
 * com.neuroassist.pac.infrastructure.persistence.repository.IdempotencyKeyJpaRepository#deleteExpiredBefore}.
 */
@Entity
@Table(name = "idempotency_key")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKeyJpaEntity {

    /**
     * Composite PK: {@code {resourceType}:{callerIdempotencyKey}}. Tenant isolation is provided by
     * the per-schema routing (search_path).
     */
    @Id
    @Column(name = "key", nullable = false, length = 500)
    private String key;

    /** UUID of the created resource (Paciente.id or Comando.id). */
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    /**
     * SHA-256 hex of the canonical request body. Mismatch with a re-submitted key triggers 409
     * {@code pac.idempotency-mismatch}.
     */
    @Column(name = "body_hash", nullable = false, length = 64)
    private String bodyHash;

    /** Creation timestamp — used for 24h TTL enforcement and lazy purge. */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
