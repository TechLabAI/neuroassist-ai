package com.neuroassist.edge.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/edge/devices (register a device). tenantId is NOT in this record —
 * derived exclusively from the JWT (api-contract §1).
 */
public record DeviceRegistrationRequest(
        @NotBlank @Size(max = 160) String label,

        /** Advisory hint for the pairing UI; does not trigger pairing. */
        String pacienteHint) {}
