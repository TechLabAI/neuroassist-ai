package com.neuroassist.edge.infrastructure.persistence.entity;

import com.neuroassist.edge.domain.model.PairingState;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

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
public class DispositivoEdgeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 120, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;
}
