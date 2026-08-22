package com.neuroassist.pac.application.port.out;

import com.neuroassist.pac.domain.model.PacienteCuidador;
import java.util.List;
import java.util.UUID;

/** Outbound port — persistence for the Paciente↔Cuidador link table. */
public interface PacienteCuidadorRepositoryPort {

    /** Persists all caregiver links for a patient (batch insert). */
    List<PacienteCuidador> saveAll(List<PacienteCuidador> cuidadores);

    /** Returns all caregiver links for a given patient. */
    List<PacienteCuidador> findByPacienteId(UUID pacienteId);

    /**
     * Removes all caregiver links for a given patient. Called before re-inserting the new set on
     * PUT to avoid orphan management complexity.
     */
    void deleteByPacienteId(UUID pacienteId);
}
