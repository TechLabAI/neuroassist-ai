package com.neuroassist.pac.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tipo_gesto_ref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoGestoRefJpaEntity {

    @Id
    @Column(name = "tipo_gesto_id")
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "supported", nullable = false)
    private boolean supported;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "tenant_id")
    private String tenantId;
}
