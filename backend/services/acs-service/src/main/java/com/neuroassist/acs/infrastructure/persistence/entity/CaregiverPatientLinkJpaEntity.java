package com.neuroassist.acs.infrastructure.persistence.entity;

import com.neuroassist.acs.domain.model.LinkStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/**
 * JPA Entity — CaregiverPatientLink. Stored in empresa schema: t_<tenantId> (schema resolved at
 * runtime via Hibernate multi-tenancy). tenantId is a logical UUID — no cross-schema @ManyToOne FK
 * (PS.003). Invariant: no physical DELETE — only status transitions (ACTIVE → REVOKED/SUSPENDED).
 */
@Entity
@Table(name = "caregiver_patient_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaregiverPatientLinkJpaEntity {

    @Id
    @GeneratedValue
    @Column(name = "caregiver_patient_link_id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /**
     * FK to CuidadorJpaEntity within the same empresa schema. Using @ManyToOne only within the same
     * schema — no cross-schema FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuidador_id", nullable = false, updatable = false)
    private CuidadorJpaEntity cuidador;

    /**
     * Logical UUID reference to patient in pac-service. No FK constraint — cross-service reference
     * via UUID only (PS.003).
     */
    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LinkStatus status;

    @Column(name = "revogado_em")
    private LocalDateTime revogadoEm;

    // Audit columns (PS.002)
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "criado_por_usuario_id", updatable = false)
    private UUID criadoPorUsuarioId;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "atualizado_por_usuario_id")
    private UUID atualizadoPorUsuarioId;
}
