package com.neuroassist.acs.application.port.in;

import com.neuroassist.acs.api.dto.CaregiverRequest;
import com.neuroassist.acs.api.dto.CaregiverResponse;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import java.util.List;
import java.util.UUID;

/**
 * Inbound port — CaregiverUseCase. Use cases for caregiver (cuidador) administration. Implemented
 * by CaregiverService (application layer).
 */
public interface CaregiverUseCase {

    /**
     * Creates a new caregiver for the given tenant. BCrypt-hashes the senha before persisting.
     * Throws LoginDuplicateException (409) if login already active in tenant.
     */
    CaregiverResponse create(CaregiverRequest request, UUID tenantId);

    /** Finds a caregiver by ID. Throws CaregiverNotFoundException (404) if not found. */
    CaregiverResponse findById(UUID cuidadorId);

    /** Lists all caregivers for the given tenant. */
    List<CaregiverResponse> listByTenant(UUID tenantId);

    /**
     * Updates the status of a caregiver (ACTIVE ↔ INACTIVE). Throws CaregiverNotFoundException
     * (404) if not found.
     */
    CaregiverResponse updateStatus(UUID cuidadorId, CuidadorStatus status);
}
