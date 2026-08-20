package com.neuroassist.acs.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO — Update caregiver status (PATCH /api/v1/acs/caregivers/{id}/status). Requires
 * authority: acs.cuidador.deactivate.
 */
public record CaregiverStatusUpdateRequest(
        @NotBlank(message = "status is required")
                @Pattern(regexp = "ACTIVE|INACTIVE", message = "status must be ACTIVE or INACTIVE")
                String status) {}
