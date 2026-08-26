package com.neuroassist.acs.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO — Create Tenant (POST /api/v1/acs/tenants). Requires authority:
 * acs.tenant.provisionar.
 *
 * <p><b>The first coordinator is part of creating a company, not a follow-up step</b> (E2E-029).
 *
 * <p>Before this, a freshly provisioned tenant was unreachable: every token belongs to a caregiver
 * and every caregiver belongs to a tenant, so with no caregiver inside it there was no actor able
 * to act there. {@code POST /caregivers?tenantId=<novo>} answered {@code 403 acs.tenant.mismatch} —
 * correctly, because the caller belongs to another tenant. The schema was perfect and inaccessible.
 * The dev seed papered over it by inserting rows straight into the database, which is why six E2E
 * sessions never hit it.
 *
 * <p>Making the coordinator part of the payload means "a company with nobody in it" stops being a
 * representable state, and no role ever needs authority to create people inside another tenant.
 */
public record TenantRequest(
        @NotBlank(message = "nome is required") String nome,
        @NotNull(message = "tipo is required") String tipo,
        @NotNull(message = "primeiroCoordenador is required") @Valid
                PrimeiroCoordenador primeiroCoordenador) {

    /**
     * The company's first COORDENADOR_CLINICA, created atomically with the tenant.
     *
     * <p>LGPD: {@code senha} is request-only — never echoed in a response and never logged.
     */
    public record PrimeiroCoordenador(
            @NotBlank(message = "primeiroCoordenador.nome is required") String nome,
            @NotBlank(message = "primeiroCoordenador.login is required") String login,
            @NotBlank(message = "primeiroCoordenador.senha is required")
                    @Size(min = 8, message = "primeiroCoordenador.senha must be at least 8 chars")
                    String senha) {}
}
