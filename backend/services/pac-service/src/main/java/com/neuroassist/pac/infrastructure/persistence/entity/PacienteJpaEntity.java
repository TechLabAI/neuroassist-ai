package com.neuroassist.pac.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * JPA entity for the {@code paciente} table.
 *
 * <p>Schema: per-empresa (tenant-scoped). Schema routing handled by MultiTenantConnectionProvider +
 * TenantContextHolder at runtime. Column mapping aligned to V1__create_initial_schema.sql.
 */
@Entity
@Table(name = "paciente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PacienteJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Empresa/tenant identifier — cross-schema isolation key (multi-tenancy). */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "nome", nullable = false)
    private String nome;

    /** Cross-service reference to EDGE DispositivoEdge (no FK — plain UUID ref). */
    @Column(name = "dispositivo_edge_id")
    private UUID dispositivoEdgeId;

    /**
     * Monotonic version counter. Incremented by the DB trigger {@code tg_paciente_touch} on each
     * UPDATE in production. In tests (ddl-auto=create-drop) the trigger is absent; the service
     * layer increments this field manually before saving.
     */
    @Column(name = "row_version", nullable = false)
    private int rowVersion;

    /**
     * Catalog version counter. Bumped atomically by {@link
     * com.neuroassist.pac.infrastructure.persistence.adapter.PacienteVersionBumperAdapter} whenever
     * a published Comando changes (publish, recalibrate, retire). Drives the ETag for {@code GET
     * .../catalogo-ativo} (PAC-019).
     */
    @Column(name = "catalogo_versao", nullable = false)
    private int catalogoVersao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** LGPD erasure timestamp — see {@code Paciente#erasedAt}. */
    @Column(name = "erased_at")
    private Instant erasedAt;
}
