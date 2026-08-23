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
     * Seeds the first COORDENADOR_CLINICA of a tenant that has none (E2E-029).
     *
     * <p><b>Bootstrap path — separate from {@link #create} on purpose.</b> {@code create} compares
     * the requested tenant against the caller's JWT tenant and rejects a mismatch, which is what
     * keeps clinic A from creating people inside clinic B. A brand-new tenant has nobody inside it
     * to be that caller, so the check cannot be satisfied and must not be relaxed: relaxing it
     * would open cross-tenant caregiver creation for every call, to fix a case that happens once
     * per company.
     *
     * <p>What replaces the check is a narrower invariant: this method refuses if the tenant already
     * has any caregiver. It can therefore only ever run once per tenant, and only from {@code
     * TenantService} during creation.
     *
     * @throws com.neuroassist.acs.shared.error.TenantAlreadySeededException if the tenant already
     *     has at least one caregiver
     */
    CaregiverResponse seedFirstCoordinator(UUID tenantId, String nome, String login, String senha);

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
