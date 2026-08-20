package com.neuroassist.acs.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO — Create Tenant (POST /api/v1/acs/tenants). Requires authority: acs.tenant.create.
 */
public record TenantRequest(
        @NotBlank(message = "nome is required") String nome,
        @NotNull(message = "tipo is required") String tipo) {}
