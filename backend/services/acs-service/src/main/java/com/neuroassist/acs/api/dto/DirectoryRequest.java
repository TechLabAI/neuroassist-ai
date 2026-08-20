package com.neuroassist.acs.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO — Directory lookup (GET /api/v1/acs/directory/*). Used as query input for directory
 * context queries. Requires authority: acs.directory.read.
 */
public record DirectoryRequest(
        @NotNull(message = "tenantId is required") UUID tenantId,
        UUID cuidadorId,
        UUID pacienteId) {}
