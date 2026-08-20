package com.neuroassist.acs.infrastructure.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.neuroassist.acs.domain.model.SigningKeyStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/**
 * JPA Entity — SigningKey (RS256 JWT signing key). Stored in neuroassist_global schema. SECURITY:
 * privateKeyEnc is @JsonIgnore — never serialized in API responses or logs. Invariant: private key
 * material NEVER leaves infrastructure layer.
 */
@Entity
@Table(schema = "neuroassist_global", name = "signing_key")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SigningKeyJpaEntity {

    @Id
    @GeneratedValue
    @Column(name = "signing_key_id")
    private UUID id;

    @Column(name = "kid", nullable = false, unique = true)
    private String kid;

    @Column(name = "algorithm", nullable = false)
    private String algorithm;

    @Column(name = "public_jwk", nullable = false, columnDefinition = "TEXT")
    private String publicJwk;

    /**
     * Encrypted private key material. Never serialized in API responses. Protected by @JsonIgnore
     * to prevent accidental exposure.
     */
    @JsonIgnore
    @Column(name = "private_key_enc", nullable = false)
    private byte[] privateKeyEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SigningKeyStatus status;

    @Column(name = "not_before")
    private LocalDateTime notBefore;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

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
