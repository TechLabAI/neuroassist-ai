package com.neuroassist.mig.api.dto;

/**
 * Body of {@code PUT /internal/migrations/tenants/{tenantId}} (E2E-016).
 *
 * <p><b>Deviation from the api-contract draft, and why.</b> The draft showed an empty body and a
 * response schema of {@code t_<tenantId>} — i.e. MIG deriving the schema name from the tenant id.
 * That is the exact defect CLAUDE.md records under "tenant id ≠ schema name": the platform already
 * paid for deriving a schema from an identifier once, and acs-service does not name schemas that
 * way either — {@code TenantProvisioningService} mints {@code "t_" + } a fresh UUID and records it
 * in {@code neuroassist_global.tenant.schema_nome}.
 *
 * <p>So the name travels in the request. acs-service owns it, MIG applies it, and neither side
 * derives it from the other's identifier. The contract doc was updated to match.
 *
 * @param schema target schema name, owned by acs-service
 */
public record ProvisionTenantRequest(String schema) {}
