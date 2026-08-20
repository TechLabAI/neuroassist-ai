package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/** Thrown when a tenant is not in ACTIVE status (e.g. still PROVISIONING). Maps to HTTP 409. */
public class TenantNotActiveException extends CodedException {

    public TenantNotActiveException(UUID tenantId, String status) {
        super(
                "acs.tenant-not-active",
                "Tenant is not active: " + tenantId + " (status=" + status + ")");
        withContext(Map.of("tenantId", tenantId.toString(), "status", status));
    }
}
