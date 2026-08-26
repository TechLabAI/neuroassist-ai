package com.neuroassist.pac.application.port.out;

import com.neuroassist.pac.domain.model.Comando;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — Comando persistence.
 *
 * <p>Implementations live in {@code infrastructure.persistence.adapter}. All methods are
 * tenant-scoped by Hibernate schema routing (MultiTenantConnectionProvider); callers do not pass
 * tenantId explicitly — the schema is already set on the connection.
 *
 * <p>Transactional boundary is in {@code application.service} (RULE-03) — adapters do NOT carry
 * {@code @Transactional}.
 */
public interface ComandoRepositoryPort {

    /**
     * Persists a new or updated command. Implementations must detect and re-throw a domain
     * exception (code {@code pac.comando.duplicate-active-mapping}, HTTP 422) when the DB unique
     * constraint {@code ux_comando_active_mapping} is violated.
     */
    Comando save(Comando entity);

    /** Finds a command by its surrogate id within the current tenant schema. */
    Optional<Comando> findById(UUID id);

    /**
     * Finds a command by id, scoped to a specific patient (prevents cross-patient access within the
     * same tenant schema).
     */
    Optional<Comando> findByIdAndPacienteId(UUID id, UUID pacienteId);

    /** Returns all commands for a patient, ordered newest-first. */
    List<Comando> findByPacienteId(UUID pacienteId);

    /** Returns non-retired commands for a patient, ordered newest-first. */
    List<Comando> findActiveByPacienteId(UUID pacienteId);

    /** Returns only published commands for a patient — feeds {@code GET .../catalogo-ativo}. */
    List<Comando> findPublishedByPacienteId(UUID pacienteId);

    /**
     * Advisory pre-flight: checks whether an active (non-retired) mapping with the same
     * gesture+need already exists for the patient, excluding {@code excludeId} (for updates).
     *
     * <p>The DB partial index {@code ux_comando_active_mapping} remains the authoritative guard;
     * this method exists to surface a meaningful 422 before the constraint fires so that the error
     * message can be more descriptive.
     */
    boolean existsActiveMapping(
            UUID pacienteId,
            UUID primitivoPrincipalId,
            UUID primitivoSecundarioId,
            String necessidade,
            UUID excludeId);
}
