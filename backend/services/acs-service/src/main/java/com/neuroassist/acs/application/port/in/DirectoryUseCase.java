package com.neuroassist.acs.application.port.in;

import com.neuroassist.acs.api.dto.DirectoryResponse;
import java.util.UUID;

/**
 * Inbound port — DirectoryUseCase. Resolves the access directory for a patient in a given tenant.
 * Used by other services (evnt, dash, hist) to determine routing targets. Implemented by
 * DirectoryService (application layer).
 */
public interface DirectoryUseCase {

    /**
     * Resolves the directory context for a patient in a tenant. Returns tenant validity info and
     * the list of active responsible caregivers. Throws TenantNotFoundException (404) if tenant not
     * found.
     *
     * @param tenantId UUID of the tenant
     * @param pacienteId UUID of the patient
     * @return DirectoryResponse with tenant info + responsible caregivers
     */
    DirectoryResponse resolveDirectory(UUID tenantId, UUID pacienteId);

    /**
     * Returns the list of active responsible caregivers for a patient. Used by GET
     * /api/v1/acs/directory/patients/{id}/caregivers (routing decisions, Cache-Control 60s).
     *
     * @param pacienteId UUID of the patient
     * @return PatientCaregiversResponse with caregivers list (empty array if none)
     */
    DirectoryResponse.PatientCaregiversResponse getPatientCaregivers(UUID pacienteId);

    /**
     * Returns tenant validity info for cross-service consumption. Used by GET
     * /api/v1/acs/directory/tenants/{id}. Throws TenantNotFoundException (404) if not found.
     *
     * @param tenantId UUID of the tenant
     * @return TenantInfoResponse with status and tipo
     */
    DirectoryResponse.TenantInfoResponse getTenantInfo(UUID tenantId);

    /**
     * Returns the set of patient UUIDs a caregiver has active links to. Used by GET
     * /api/v1/acs/directory/caregivers/{id}/patient-scope.
     *
     * @param cuidadorId UUID of the caregiver
     * @return PatientScopeResponse with list of patient UUIDs
     */
    DirectoryResponse.PatientScopeResponse getCaregiverPatientScope(UUID cuidadorId);
}
