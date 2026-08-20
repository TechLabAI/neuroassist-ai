package com.neuroassist.pac.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite primary key for paciente_cuidador (paciente_id, cuidador_responsavel_id). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PacienteCuidadorId implements Serializable {

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(name = "cuidador_responsavel_id", nullable = false)
    private UUID cuidadorResponsavelId;
}
