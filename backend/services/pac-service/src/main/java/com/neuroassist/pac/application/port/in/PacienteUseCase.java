package com.neuroassist.pac.application.port.in;

import com.neuroassist.pac.domain.model.Paciente;
import java.util.List;
import java.util.UUID;

/**
 * Inbound port — Paciente CRUD use cases.
 *
 * <p>Accepts domain-primitive parameters; the controller is responsible for converting HTTP DTOs.
 * No Spring/JPA/HTTP imports allowed here (RULE-01/RULE-02 apply conceptually to all ports).
 */
public interface PacienteUseCase {

    /**
     * Creates a new Paciente scoped to the current tenant (from TenantContextHolder).
     *
     * @param nome patient name (non-blank)
     * @param dispositivoEdgeId optional EDGE device reference (null if absent)
     * @param cuidadorResponsavelIds ACS caregiver UUIDs — not validated in P2 (P4 adds ACS client)
     * @return persisted Paciente with id, rowVersion and timestamps populated
     */
    Paciente create(String nome, UUID dispositivoEdgeId, List<UUID> cuidadorResponsavelIds);

    /**
     * Finds a Paciente by id, scoped to the current tenant.
     *
     * @throws com.neuroassist.pac.shared.error.CodedException with code "not_found.paciente" if
     *     absent or belongs to another tenant
     */
    Paciente findById(UUID id);

    /** Returns all Pacientes for the current tenant. */
    List<Paciente> listAll();

    /**
     * Updates a Paciente if the provided ETag version matches (optimistic locking).
     *
     * @param id Paciente id
     * @param expectedEtagVersion parsed ETag value from the {@code If-Match} header (e.g. 1 from
     *     {@code "1"}); must equal {@code rowVersion + 1}
     * @param nome new name (non-blank)
     * @param dispositivoEdgeId new device reference (null to clear)
     * @param cuidadorResponsavelIds new caregiver list (empty to clear)
     * @throws com.neuroassist.pac.shared.error.CodedException with code "conflict-precondition" if
     *     ETag is stale
     */
    Paciente update(
            UUID id,
            int expectedEtagVersion,
            String nome,
            UUID dispositivoEdgeId,
            List<UUID> cuidadorResponsavelIds);
}
