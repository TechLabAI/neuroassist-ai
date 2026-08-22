package com.neuroassist.pac.application.port.out;

import java.util.List;

/**
 * Outbound port — lists the tenants this instance serves.
 *
 * <p>Exists because background work has no request to inherit a tenant from. Every table
 * pac-service owns lives in a per-tenant schema, so a sweeper that does not iterate tenants
 * explicitly reads whatever schema the connection happens to point at — in practice the global one,
 * where none of these tables exist.
 */
public interface TenantCatalogPort {

    /** Tenant identifiers with {@code status = ACTIVE}, from the global catalogue. */
    List<String> activeTenantIds();
}
