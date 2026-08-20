package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/** Thrown when attempting to authenticate or operate on a SUSPENDED tenant. Maps to HTTP 409. */
public class TenantSuspendedException extends CodedException {

    public TenantSuspendedException(UUID tenantId) {
        super("acs.tenant-suspended", "Tenant is suspended: " + tenantId);
        withContext(Map.of("tenantId", tenantId.toString()));
    }
}
