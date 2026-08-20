package com.neuroassist.acs.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO — Create Caregiver-Patient Link (POST
 * /api/v1/acs/caregivers/{cuidadorId}/patients/{pacienteId}/link). Requires authority:
 * acs.link.create.
 */
public record CaregiverPatientLinkRequest(
        @NotNull(message = "pacienteId is required") UUID pacienteId) {}
