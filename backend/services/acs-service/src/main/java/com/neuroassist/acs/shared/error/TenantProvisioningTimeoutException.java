package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/**
 * Thrown when tenant provisioning polling exceeds the maximum wait time (30s). Maps to HTTP 504.
 * Tenant remains in PROVISIONING status for manual retry.
 */
public class TenantProvisioningTimeoutException extends CodedException {

    public TenantProvisioningTimeoutException(UUID tenantId) {
        super(
                "acs.tenant.provisioning-timeout",
                "Tenant provisioning timed out after 30s: " + tenantId);
        withContext(Map.of("tenantId", tenantId.toString()));
    }
}
