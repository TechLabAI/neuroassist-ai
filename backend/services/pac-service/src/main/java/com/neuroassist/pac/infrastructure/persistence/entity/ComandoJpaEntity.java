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
 * JPA entity for the {@code comando} table.
 *
 * <p>Schema: per-empresa (tenant-scoped). Schema routing handled by {@link
 * com.neuroassist.pac.infrastructure.security.MultiTenantConnectionProvider} + {@link
 * com.neuroassist.pac.infrastructure.security.TenantContextHolder} at runtime.
 *
 * <p>Column mapping aligned to V0201__pac_comando.sql.
 *
 * <p>Unique constraint {@code ux_comando_active_mapping} is enforced at DB level (partial index
 * WHERE estado &lt;&gt; 'retired'). The adapter catches {@code DataIntegrityViolationException}
 * from this constraint and re-throws a domain {@code CodedException}.
 */
@Entity
@Table(name = "comando")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComandoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** FK to paciente in the same schema. */
    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    @Column(name = "rotulo", nullable = false)
    private String rotulo;

    @Column(name = "necessidade", nullable = false)
    private String necessidade;

    /** Cross-service reference to GEST TipoGesto (no DB FK). */
    @Column(name = "primitivo_principal_id", nullable = false)
    private UUID primitivoPrincipalId;

    /** Optional secondary primitive. Cross-service reference to GEST TipoGesto (no DB FK). */
    @Column(name = "primitivo_secundario_id")
    private UUID primitivoSecundarioId;

    /** Enum stored as text: baixa | media | alta */
    @Column(name = "sensibilidade", nullable = false)
    private String sensibilidade;

    @Column(name = "tempo_min_gesto_ms", nullable = false)
    private int tempoMinGestoMs;

    @Column(name = "tempo_max_gesto_ms", nullable = false)
    private int tempoMaxGestoMs;

    /** Enum stored as text: emergencia | rotina */
    @Column(name = "urgencia", nullable = false)
    private String urgencia;

    /** State machine: drafted | validated | published | retired */
    @Column(name = "estado", nullable = false)
    private String estado;

    /** Monotonic; +1 on each in-place recalibration of a published command (ADR-004). */
    @Column(name = "calibracao_versao", nullable = false)
    private int calibracaoVersao;

    /**
     * Optimistic concurrency version. Bumped by trigger {@code tg_comando_touch} on UPDATE. Backs
     * the HTTP ETag/If-Match contract.
     */
    @Column(name = "row_version", nullable = false)
    private int rowVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
