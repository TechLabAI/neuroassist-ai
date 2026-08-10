package com.neuroassist.gest.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "detection_profile_primitive")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectionProfilePrimitiveJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "tipo_gesto_id", nullable = false)
    private String tipoGestoId;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "engine_feature_ref")
    private String engineFeatureRef;
}
