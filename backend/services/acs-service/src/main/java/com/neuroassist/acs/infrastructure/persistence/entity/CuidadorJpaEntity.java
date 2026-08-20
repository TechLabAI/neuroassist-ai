package com.neuroassist.acs.infrastructure.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.neuroassist.acs.domain.model.CuidadorPapel;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/**
 * JPA Entity — Cuidador. Stored in empresa schema: t_<tenantId> (schema resolved at runtime via
 * Hibernate multi-tenancy). SECURITY: credentialHash is @JsonIgnore — never serialized in API
 * responses. Invariant: no physical DELETE — only status transitions.
 */
@Entity
@Table(name = "cuidador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuidadorJpaEntity {

    @Id
    @GeneratedValue
    @Column(name = "cuidador_id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "login", nullable = false)
    private String login;

    /**
     * BCrypt credential hash. Never serialized in API responses. Protected by @JsonIgnore to
     * prevent accidental exposure.
     */
    @JsonIgnore
    @Column(name = "credential_hash", nullable = false)
    private String credentialHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false)
    private CuidadorPapel papel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CuidadorStatus status;

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
