package com.neuroassist.pac.application.port.out;

import com.neuroassist.pac.domain.model.Paciente;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port — persistence for Paciente. Implemented by PacienteRepositoryAdapter. */
public interface PacienteRepositoryPort {

    /** Persists a new Paciente or updates an existing one. */
    Paciente save(Paciente paciente);

    /**
     * Finds a Paciente by id scoped to a specific tenant.
     *
     * <p>Returns empty when the id does not exist OR belongs to another tenant (multi-tenancy
     * defense-in-depth on top of TenantContextHolder).
     */
    Optional<Paciente> findByIdAndTenantId(UUID id, String tenantId);

    /** Returns all Pacientes belonging to the given tenant. */
    List<Paciente> findByTenantId(String tenantId);
}
