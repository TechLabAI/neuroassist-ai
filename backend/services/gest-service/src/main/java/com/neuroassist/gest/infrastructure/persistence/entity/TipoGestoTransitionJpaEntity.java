package com.neuroassist.gest.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tipo_gesto_transition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoGestoTransitionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "tipo_gesto_id", nullable = false)
    private String tipoGestoId;

    @Column(name = "from_state", nullable = false)
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    @Column(name = "actor")
    private String actor;

    @Column(name = "reason")
    private String reason;

    @Column(name = "at", nullable = false)
    private Instant at;
}
