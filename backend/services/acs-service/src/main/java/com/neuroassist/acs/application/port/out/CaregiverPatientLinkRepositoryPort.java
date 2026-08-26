package com.neuroassist.acs.application.port.out;

import com.neuroassist.acs.domain.model.CaregiverPatientLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — CaregiverPatientLink repository. Operates on the empresa schema (t_<tenantId>)
 * resolved via Hibernate multi-tenancy. Zero Spring/JPA imports (RULE-01, RULE-02).
 *
 * <p>Invariante D-204 (SAFETY-CRITICAL): countActiveByPacienteId MUST be called before revoking a
 * link to enforce coverage invariant at application layer.
 */
public interface CaregiverPatientLinkRepositoryPort {

    /** Persists a new or updated link (status transitions only — no physical delete). */
    CaregiverPatientLink save(CaregiverPatientLink entity);

    /** Finds a link by its ID. */
    Optional<CaregiverPatientLink> findById(UUID id);

    /** Lists all links for a tenant. */
    List<CaregiverPatientLink> findByTenantId(UUID tenantId);

    /**
     * Returns all ACTIVE links for a given patient. Used by DirectoryService to build routing
     * context.
     *
     * @param pacienteId logical patient UUID (cross-service reference)
     * @return list of active CaregiverPatientLink for the patient
     */
    List<CaregiverPatientLink> findActiveByPacienteId(UUID pacienteId);

    /**
     * Returns the ACTIVE link between a specific caregiver and patient, if it exists. Used by
     * CaregiverPatientLinkService for revocation.
     *
     * @param cuidadorId caregiver UUID
     * @param pacienteId patient UUID
     * @return Optional active link
     */
    Optional<CaregiverPatientLink> findActiveByCuidadorIdAndPacienteId(
            UUID cuidadorId, UUID pacienteId);

    /**
     * Counts ACTIVE links for a given patient. SAFETY-CRITICAL (D-204): used to enforce coverage
     * invariant before revoking.
     *
     * @param pacienteId patient UUID
     * @return count of active links for this patient
     */
    long countActiveByPacienteId(UUID pacienteId);

    /**
     * Returns all ACTIVE links for a given caregiver. Used by DirectoryService to build
     * patient-scope context.
     *
     * @param cuidadorId caregiver UUID
     * @return list of active CaregiverPatientLink for the caregiver
     */
    List<CaregiverPatientLink> findActiveByCuidadorId(UUID cuidadorId);

    /**
     * Returns any link (regardless of status) between a specific caregiver and patient. Used for
     * idempotent revocation — if already revoked, return the revoked link.
     */
    Optional<CaregiverPatientLink> findByCuidadorIdAndPacienteId(UUID cuidadorId, UUID pacienteId);
}
