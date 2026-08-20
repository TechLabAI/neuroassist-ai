package com.neuroassist.acs.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO — Directory context response. Provides tenant validity and routing information for
 * other services. LGPD: no credential or private fields exposed.
 *
 * <p>Used by: evnt-service, dash-service, hist-service for authorization decisions.
 */
public record DirectoryResponse(
        UUID tenantId,
        String tenantStatus,
        boolean tenantValid,
        String tenantTipo,
        List<ResponsibleCaregiver> responsibleCaregivers) {

    /**
     * Responsible caregiver context for routing decisions.
     *
     * @param cuidadorId UUID of the caregiver
     * @param papel caregiver role
     * @param authorities list of granted authorities
     */
    public record ResponsibleCaregiver(UUID cuidadorId, String papel, List<String> authorities) {}

    /**
     * Response for GET /api/v1/acs/directory/patients/{id}/caregivers. Returns list of active
     * responsible caregivers for the patient.
     */
    public record PatientCaregiversResponse(
            UUID pacienteId, List<ResponsibleCaregiver> responsibleCaregivers) {}

    /**
     * Response for GET /api/v1/acs/directory/tenants/{id}. Returns tenant validity info for
     * cross-service consumption.
     */
    public record TenantInfoResponse(UUID tenantId, String status, String tipo, boolean valid) {}

    /**
     * Response for GET /api/v1/acs/directory/caregivers/{id}/patient-scope. Returns the set of
     * patient UUIDs a caregiver has active links to.
     */
    public record PatientScopeResponse(UUID cuidadorId, List<UUID> pacienteIds) {}
}
