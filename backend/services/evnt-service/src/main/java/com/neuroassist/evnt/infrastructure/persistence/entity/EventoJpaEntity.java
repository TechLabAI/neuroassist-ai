package com.neuroassist.evnt.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "paciente_id", nullable = false, length = 64)
    private String pacienteId;

    @Column(name = "comando_ref", length = 255)
    private String comandoRef;

    @Column(name = "command_label_snapshot", length = 255)
    private String commandLabelSnapshot;

    @Column(name = "urgencia_snapshot", nullable = false, length = 50)
    private String urgenciaSnapshot;

    @Column(name = "feedback", nullable = false, length = 50)
    @Builder.Default
    private String feedback = "NAO_AVALIADO";

    @Column(name = "confirmado_por", length = 255)
    private String confirmadoPor;

    @Column(name = "confirmado_at")
    private Instant confirmadoAt;

    @Column(name = "recognized_at")
    private Instant recognizedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
