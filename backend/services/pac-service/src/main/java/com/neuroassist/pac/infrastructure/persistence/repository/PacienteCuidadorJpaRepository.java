package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.PacienteCuidadorId;
import com.neuroassist.pac.infrastructure.persistence.entity.PacienteCuidadorJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@code paciente_cuidador}. */
public interface PacienteCuidadorJpaRepository
        extends JpaRepository<PacienteCuidadorJpaEntity, PacienteCuidadorId> {

    /** Returns all caregiver links for a given patient. */
    List<PacienteCuidadorJpaEntity> findByIdPacienteId(UUID pacienteId);

    /** Deletes all caregiver links for a given patient (used on PUT to replace the set). */
    @Modifying
    @Query("DELETE FROM PacienteCuidadorJpaEntity pc WHERE pc.id.pacienteId = :pacienteId")
    void deleteByPacienteId(@Param("pacienteId") UUID pacienteId);
}
