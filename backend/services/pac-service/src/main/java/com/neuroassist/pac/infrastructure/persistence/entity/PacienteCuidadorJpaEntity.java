package com.neuroassist.pac.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the {@code paciente_cuidador} join table.
 *
 * <p>Composite PK: (paciente_id, cuidador_responsavel_id). {@code cuidador_responsavel_id} is a
 * cross-service reference to ACS Cuidador (no FK — plain UUID). Schema: per-empresa (tenant row
 * implicitly scoped via paciente FK).
 */
@Entity
@Table(name = "paciente_cuidador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PacienteCuidadorJpaEntity {

    @EmbeddedId private PacienteCuidadorId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
