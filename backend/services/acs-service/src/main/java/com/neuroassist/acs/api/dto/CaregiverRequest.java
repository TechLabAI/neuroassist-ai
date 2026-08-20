package com.neuroassist.acs.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO — Create Caregiver (POST /api/v1/acs/caregivers). Requires authority:
 * acs.cuidador.create. LGPD: senha field is request-only — never returned in responses.
 */
public record CaregiverRequest(
        @NotBlank(message = "nome is required") String nome,
        @NotBlank(message = "login is required") String login,
        @NotNull(message = "papel is required") String papel,
        @NotBlank(message = "senha is required") String senha) {}
