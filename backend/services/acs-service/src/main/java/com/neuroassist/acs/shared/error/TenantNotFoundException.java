package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/** Thrown when a tenant with the given ID does not exist. Maps to HTTP 404. */
public class TenantNotFoundException extends CodedException {

    public TenantNotFoundException(UUID tenantId) {
        super("acs.tenant.not-found", "Tenant not found: " + tenantId);
        withContext(Map.of("tenantId", tenantId.toString()));
    }
}
