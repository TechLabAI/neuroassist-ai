package com.neuroassist.mig.application.port.out;

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
}
