package com.neuroassist.acs.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — PacPatientResolvePort. Communicates with pac-service to resolve patient
 * information. Timeout: 2s (fail-closed). Circuit breaker enforced at adapter level. Zero
 * Spring/JPA imports (RULE-01, RULE-02, RULE-06).
 */
public interface PacPatientResolvePort {

    /**
     * Resolves patient identity from pac-service.
     *
     * @param pacienteId UUID of the patient
     * @param tenantId UUID of the tenant context
     * @return Optional containing patient resolution result, or empty if patient not found
     */
    Optional<PatientResolveResponse> resolvePatient(UUID pacienteId, UUID tenantId);

    /**
     * Patient resolution response from pac-service.
     *
     * @param pacienteId UUID of the resolved patient
     * @param tenantId UUID of the tenant
     * @param nome patient name
     * @param status patient status (ACTIVE, INACTIVE, etc.)
     */
    record PatientResolveResponse(UUID pacienteId, UUID tenantId, String nome, String status) {}
}
