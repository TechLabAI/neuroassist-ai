package com.neuroassist.acs.application.port.in;

import com.neuroassist.acs.api.dto.CaregiverPatientLinkResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port — CaregiverPatientLinkUseCase. Use cases for caregiver-patient link management.
 * Implemented by CaregiverPatientLinkService (application layer).
 */
public interface CaregiverPatientLinkUseCase {

    /**
     * Creates an active link between a caregiver and a patient. Verifies that caregiver exists and
     * belongs to the requested tenant. Throws CaregiverNotFoundException (404) if caregiver not
     * found. Throws TenantMismatchException (403) if caregiver belongs to different tenant.
     *
     * @param cuidadorId UUID of the caregiver
     * @param pacienteId UUID of the patient (logical cross-service reference)
     * @param tenantId UUID of the tenant context
     */
    CaregiverPatientLinkResponse createLink(UUID cuidadorId, UUID pacienteId, UUID tenantId);

    /**
     * Revokes the active link between a caregiver and a patient. SAFETY-CRITICAL (D-204): throws
     * LastCaregiverLinkException (409) if this would leave the patient without any active
     * caregiver.
     *
     * @param cuidadorId UUID of the caregiver
     * @param pacienteId UUID of the patient
     */
    CaregiverPatientLinkResponse revokeLink(UUID cuidadorId, UUID pacienteId);

    /**
     * Finds the active link between a caregiver and a patient, if it exists. Used by the controller
     * to decide HTTP 200 (idempotent) vs 201 (new link created).
     *
     * @param cuidadorId UUID of the caregiver
     * @param pacienteId UUID of the patient
     * @return Optional with the active link response, or empty if no active link exists
     */
    Optional<CaregiverPatientLinkResponse> findActiveLink(UUID cuidadorId, UUID pacienteId);
}
