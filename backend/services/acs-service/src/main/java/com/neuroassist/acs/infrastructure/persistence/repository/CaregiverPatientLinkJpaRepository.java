package com.neuroassist.acs.infrastructure.persistence.repository;

import com.neuroassist.acs.domain.model.LinkStatus;
import com.neuroassist.acs.infrastructure.persistence.entity.CaregiverPatientLinkJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaregiverPatientLinkJpaRepository
        extends JpaRepository<CaregiverPatientLinkJpaEntity, UUID> {

    /**
     * Lists all links for a given tenant ID.
     *
     * @param tenantId tenant UUID
     * @return list of links for the tenant
     */
    List<CaregiverPatientLinkJpaEntity> findByTenantId(UUID tenantId);

    /**
     * Returns all links for a given patient with the specified status. Used by DirectoryService to
     * resolve responsible caregivers.
     *
     * @param pacienteId patient UUID (cross-service logical reference)
     * @param status link status filter
     * @return list of matching links
     */
    List<CaregiverPatientLinkJpaEntity> findByPacienteIdAndStatus(
            UUID pacienteId, LinkStatus status);

    /**
     * Returns the link between a specific caregiver and patient with the given status. Used by
     * CaregiverPatientLinkService for revocation lookup.
     *
     * @param cuidadorId caregiver entity ID (via @ManyToOne join column)
     * @param pacienteId patient UUID
     * @param status link status
     * @return Optional matching link
     */
    Optional<CaregiverPatientLinkJpaEntity> findByCuidador_IdAndPacienteIdAndStatus(
            UUID cuidadorId, UUID pacienteId, LinkStatus status);

    /**
     * Counts links for a given patient with the specified status. SAFETY-CRITICAL (D-204): used to
     * enforce coverage invariant before revoking last link.
     *
     * @param pacienteId patient UUID
     * @param status link status filter
     * @return count of matching links
     */
    long countByPacienteIdAndStatus(UUID pacienteId, LinkStatus status);

    /**
     * Returns all links for a given caregiver with the specified status. Used by DirectoryService
     * to resolve the patient scope for a caregiver.
     *
     * @param cuidadorId caregiver entity ID (via @ManyToOne join column)
     * @param status link status filter
     * @return list of matching links
     */
    List<CaregiverPatientLinkJpaEntity> findByCuidador_IdAndStatus(
            UUID cuidadorId, LinkStatus status);

    /**
     * Returns the most recent link (any status) between a caregiver and patient.
     * Used for idempotent revocation — if already revoked, return the revoked link.
     */
    Optional<CaregiverPatientLinkJpaEntity> findFirstByCuidador_IdAndPacienteIdOrderByCriadoEmDesc(
            UUID cuidadorId, UUID pacienteId);
}
