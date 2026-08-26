package com.neuroassist.evnt.application.port.out;

import java.util.List;

/**
 * Outbound port — lists the tenants this instance serves.
 *
 * <p>Exists because background work has no request to inherit a tenant from, and every table
 * evnt-service owns lives in a per-tenant schema.
 */
public interface TenantCatalogPort {

    /** Tenant identifiers with {@code status = ACTIVE}, from the global catalogue. */
    List<String> activeTenantIds();
}
