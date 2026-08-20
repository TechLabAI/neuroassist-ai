package com.neuroassist.acs.infrastructure.persistence.entity;

import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.domain.model.TenantTipo;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/**
 * JPA Entity — Tenant. Stored in neuroassist_global schema. Invariant: no physical DELETE — only
 * status transitions.
 */
@Entity
@Table(schema = "neuroassist_global", name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantJpaEntity {

    @Id
    @Column(name = "tenant_id")
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TenantTipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status;

    @Column(name = "schema_nome")
    private String schemaNome;

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
