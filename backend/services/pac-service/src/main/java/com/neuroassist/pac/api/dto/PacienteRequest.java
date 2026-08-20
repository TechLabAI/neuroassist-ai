package com.neuroassist.pac.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for POST/PUT /api/pac/v1/pacientes.
 *
 * <p>Bean Validation rejects blank nome before reaching the service layer. {@code
 * dispositivoEdgeId} and {@code cuidadorResponsavelIds} are optional cross-service references
 * (validated against EDGE/ACS in P4; stored as-is in P2).
 */
public record PacienteRequest(
        @NotBlank(message = "nome must not be blank") String nome,
        UUID dispositivoEdgeId,
        List<UUID> cuidadorResponsavelIds) {}
