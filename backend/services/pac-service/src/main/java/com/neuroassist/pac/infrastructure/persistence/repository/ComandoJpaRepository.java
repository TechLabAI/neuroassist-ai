package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.ComandoJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link ComandoJpaEntity}.
 *
 * <p>Schema routing (schema-per-tenant) is transparent here — Hibernate routes the connection to
 * the correct schema via {@link
 * com.neuroassist.pac.infrastructure.security.MultiTenantConnectionProvider}.
 *
 * <p>Duplicate active mapping detection relies on the partial unique index {@code
 * ux_comando_active_mapping} (V0201) at DB level. The adapter catches the resulting {@code
 * DataIntegrityViolationException} and converts it to a domain exception.
 */
public interface ComandoJpaRepository extends JpaRepository<ComandoJpaEntity, UUID> {

    /**
     * Returns all commands belonging to a patient, ordered by creation time descending. Used by
     * authoring and catalog endpoints.
     */
    List<ComandoJpaEntity> findByPacienteIdOrderByCreatedAtDesc(UUID pacienteId);

    /** Returns non-retired commands for a patient, ordered by creation time descending. */
    List<ComandoJpaEntity> findByPacienteIdAndEstadoNotOrderByCreatedAtDesc(UUID pacienteId, String estado);

    /** Returns only published commands for a patient — feeds {@code GET .../catalogo-ativo}. */
    List<ComandoJpaEntity> findByPacienteIdAndEstado(UUID pacienteId, String estado);

    /**
     * Returns a command by id, ensuring it belongs to the expected patient. Guards against
     * cross-patient access within the same tenant schema.
     */
    Optional<ComandoJpaEntity> findByIdAndPacienteId(UUID id, UUID pacienteId);

    /**
     * Checks whether an active (non-retired) mapping of the same gesture+need already exists for
     * the patient. Used as a pre-flight guard before insert to surface a meaningful 422 before the
     * DB unique constraint fires.
     *
     * <p>Note: the partial index {@code ux_comando_active_mapping} remains the authoritative guard
     * — this check is advisory and subject to TOCTOU in concurrent inserts. The adapter also
     * catches {@code DataIntegrityViolationException} as a safety net.
     *
     * @param pacienteId the owning patient
     * @param primitivoPrincipalId main gesture primitive
     * @param primitivoSecundarioId secondary gesture primitive (may be null)
     * @param necessidade the need string
     * @param excludeId exclude this command id from the check (for update scenarios)
     */
    @Query(
            """
            SELECT COUNT(c) > 0 FROM ComandoJpaEntity c
            WHERE c.pacienteId = :pacienteId
              AND c.primitivoPrincipalId = :primitivoPrincipalId
              AND (
                    (:primitivoSecundarioId IS NULL AND c.primitivoSecundarioId IS NULL)
                    OR c.primitivoSecundarioId = :primitivoSecundarioId
                  )
              AND c.necessidade = :necessidade
              AND c.estado <> 'retired'
              AND c.id <> :excludeId
            """)
    boolean existsActiveMapping(
            @Param("pacienteId") UUID pacienteId,
            @Param("primitivoPrincipalId") UUID primitivoPrincipalId,
            @Param("primitivoSecundarioId") UUID primitivoSecundarioId,
            @Param("necessidade") String necessidade,
            @Param("excludeId") UUID excludeId);
}
