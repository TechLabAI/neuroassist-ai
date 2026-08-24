package com.neuroassist.mig.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — the global tenant registry (`neuroassist_global.tenant`).
 *
 * <p>Read-only here. acs-service owns that table; MIG only needs to answer "which schema belongs to
 * this tenant" for the status path, and to refuse provisioning a tenant nobody registered.
 */
public interface TenantCatalogPort {

    /** The recorded schema of a tenant, empty when the tenant is unknown or has no schema yet. */
    Optional<String> schemaOf(UUID tenantId);

    /** Whether the tenant exists in the registry at all. */
    boolean tenantExists(UUID tenantId);

    /**
     * Every schema recorded in the registry, for the fan-out of {@code migrate-all} (E2E-030).
     *
     * <p>Ordered deterministically. A migration run that visits schemas in a different order each
     * time makes a partial failure impossible to reason about — "it stopped after three" says
     * nothing unless the three are always the same three.
     *
     * <p>Tenants with no schema yet are excluded: they are mid-provisioning, and the provisioning
     * call is what brings them to head.
     */
    List<String> allTenantSchemas();
}
